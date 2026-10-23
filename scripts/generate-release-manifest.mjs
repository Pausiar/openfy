#!/usr/bin/env node
/**
 * Genera `web/downloads/latest.json`: el manifiesto de descarga que consume la web.
 *
 * Es la fuente única de datos del botón de descarga: versión, tamaño, SHA-256 del
 * APK y huella del certificado de firma. La web lo lee en tiempo de ejecución y, si
 * no puede leerlo, usa los valores estáticos incrustados en el HTML.
 *
 * Uso:
 *   # Con la APK en local (workflow de release):
 *   node scripts/generate-release-manifest.mjs --apk app-release.apk --tag v0.2.3
 *
 *   # Consultando la API de GitHub (workflow de Pages, sin la APK a mano):
 *   node scripts/generate-release-manifest.mjs --tag v0.2.3
 *
 * Opciones:
 *   --apk <ruta>     APK local para calcular el SHA-256 y el tamaño.
 *   --tag <vX.Y.Z>   Tag de la release (define la versión publicada).
 *   --out <ruta>     Fichero de salida (por defecto web/downloads/latest.json).
 *   --base-url <url> URL pública donde se sirve la web.
 *   --release-url    URL de la release en GitHub (por defecto se deduce del tag).
 */
import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import process from 'node:process';

const REPO_ROOT = resolve(dirname(new URL(import.meta.url).pathname).replace(/^\/([A-Za-z]:)/, '$1'), '..');

function parseArgs(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (!arg.startsWith('--')) continue;
    const key = arg.slice(2);
    if (key === 'help') {
      args.help = true;
      continue;
    }
    const next = argv[i + 1];
    if (next === undefined || next.startsWith('--')) {
      args[key] = true;
    } else {
      args[key] = next;
      i += 1;
    }
  }
  return args;
}

async function readProperties(path) {
  const raw = await readFile(path, 'utf8');
  const properties = {};
  for (const line of raw.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const index = trimmed.indexOf('=');
    if (index === -1) continue;
    properties[trimmed.slice(0, index).trim()] = trimmed.slice(index + 1).trim();
  }
  return properties;
}

/** Lee la huella pública del certificado de release (fichero versionado en el repo). */
async function readCertificate(path) {
  const raw = await readFile(path, 'utf8');
  const certificate = { subject: '', sha256: '', sha1: '' };
  for (const line of raw.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const separator = trimmed.indexOf(':');
    if (separator === -1) continue;
    const key = trimmed.slice(0, separator).trim().toLowerCase();
    const value = trimmed.slice(separator + 1).trim();
    if (key === 'subject') certificate.subject = value;
    else if (key === 'sha-256' || key === 'sha256') certificate.sha256 = value.replace(/[^0-9a-fA-F]/g, '').toLowerCase();
    else if (key === 'sha-1' || key === 'sha1') certificate.sha1 = value.replace(/[^0-9a-fA-F]/g, '').toLowerCase();
  }
  return certificate;
}

async function sha256File(path) {
  const hash = createHash('sha256');
  const { readFile: read } = await import('node:fs/promises');
  const buffer = await read(path);
  hash.update(buffer);
  return hash.digest('hex');
}

/** Metadatos del asset `openfy-latest.apk` de una release de GitHub. */
async function fetchReleaseAsset(tag, repo) {
  const token = process.env.GITHUB_TOKEN || process.env.GH_TOKEN;
  const headers = {
    Accept: 'application/vnd.github+json',
    'User-Agent': 'openfy-release-manifest',
  };
  if (token) headers.Authorization = `Bearer ${token}`;

  const response = await fetch(`https://api.github.com/repos/${repo}/releases/tags/${tag}`, { headers });
  if (!response.ok) {
    throw new Error(`No se pudo consultar la release ${tag}: HTTP ${response.status}`);
  }
  const release = await response.json();
  const asset = (release.assets || []).find((item) => item.name === 'openfy-latest.apk');
  if (!asset) {
    throw new Error(`La release ${tag} no tiene el asset openfy-latest.apk`);
  }
  return {
    sizeBytes: asset.size,
    sha256: (asset.digest || '').replace(/^sha256:/, '').toLowerCase(),
    releasedAt: release.published_at || release.created_at,
    htmlUrl: release.html_url,
  };
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) {
    console.log('Uso: node scripts/generate-release-manifest.mjs [--apk ruta] --tag vX.Y.Z [--out ruta]');
    return;
  }

  const tag = String(args.tag || '').trim();
  if (!tag) throw new Error('Falta --tag (por ejemplo --tag v0.2.3)');

  const repo = process.env.GITHUB_REPOSITORY || 'Pausiar/openfy';
  const baseUrl = String(args['base-url'] || 'https://pausiar.github.io/openfy').replace(/\/+$/, '');
  const outPath = resolve(REPO_ROOT, String(args.out || 'web/downloads/latest.json'));

  const properties = await readProperties(resolve(REPO_ROOT, 'version.properties'));
  const certificate = await readCertificate(resolve(REPO_ROOT, 'signing/release-certificate.txt'));

  let sizeBytes = null;
  let sha256 = '';
  let releasedAt = new Date().toISOString();
  let releaseUrl = `https://github.com/${repo}/releases/tag/${tag}`;

  if (args.apk) {
    const apkPath = resolve(REPO_ROOT, String(args.apk));
    const { statSync } = await import('node:fs');
    sizeBytes = statSync(apkPath).size;
    sha256 = await sha256File(apkPath);
  } else {
    const asset = await fetchReleaseAsset(tag, repo);
    sizeBytes = asset.sizeBytes;
    sha256 = asset.sha256;
    releasedAt = asset.releasedAt;
    if (asset.htmlUrl) releaseUrl = asset.htmlUrl;
  }

  if (!sha256) {
    throw new Error('No se ha podido determinar el SHA-256 del APK.');
  }

  const publishedVersion = tag.replace(/^v/, '');
  const sourceVersion = properties.versionName || '';

  const manifest = {
    versionName: publishedVersion,
    // El versionCode solo se publica cuando el tag corresponde a la versión que
    // hay en el repositorio: en el resto de casos es el de una versión anterior.
    versionCode: publishedVersion === sourceVersion ? Number(properties.versionCode) || null : null,
    releasedAt,
    apk: {
      file: 'openfy-latest.apk',
      path: 'downloads/openfy-latest.apk',
      url: `${baseUrl}/downloads/openfy-latest.apk`,
      sizeBytes,
      sha256,
    },
    signature: certificate,
    requirements: {
      minAndroid: '9',
      minSdk: 28,
    },
    mirrors: [
      {
        label: 'GitHub Releases',
        url: `https://github.com/${repo}/releases/latest/download/openfy-latest.apk`,
      },
      {
        label: `Release ${tag}`,
        url: releaseUrl,
      },
    ],
  };

  await mkdir(dirname(outPath), { recursive: true });
  await writeFile(outPath, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
  console.log(`Manifiesto escrito en ${outPath}`);
  console.log(`  versión   : ${manifest.versionName}`);
  console.log(`  tamaño    : ${sizeBytes} bytes`);
  console.log(`  sha256    : ${sha256}`);
  console.log(`  firma     : ${certificate.sha256 || '(sin datos de firma)'}`);
}

main().catch((error) => {
  console.error(`Error: ${error.message}`);
  process.exit(1);
});
