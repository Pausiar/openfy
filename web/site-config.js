/**
 * Openfy — configuración del sitio.
 *
 * Datos legales del titular del sitio web. Se rellenan aquí una sola vez y las
 * páginas legales (aviso legal, privacidad, cookies y condiciones de uso) los
 * leen de este objeto, de forma que no hay que editar cuatro HTML para cambiar
 * un dato.
 *
 * ⚠️  Los campos vacíos se muestran en la web marcados como "pendiente de
 *     completar". Antes de publicar el sitio hay que rellenarlos: son datos que
 *     exige el artículo 10 de la Ley 34/2002 (LSSI) y el RGPD.
 */
window.OPENFY_SITE = {
  legal: {
    /** Denominación social o nombre del titular del sitio. */
    name: '',
    /** NIF / CIF / NIE del titular. */
    taxId: '',
    /** Domicilio a efectos de notificaciones. */
    address: '',
    /** Correo de contacto (se publica en la web). */
    email: '',
    /** Prestador del alojamiento web. */
    hosting: 'GitHub, Inc.',
    hostingService: 'GitHub Pages',
    /** Autoridad de control: Agencia Española de Protección de Datos. */
    supervisoryAuthority: 'Agencia Española de Protección de Datos (AEPD)',
    supervisoryAuthorityUrl: 'https://www.aepd.es/',
  },
  site: {
    /** URL pública del sitio. */
    url: 'https://pausiar.github.io/openfy/',
    /** Repositorio del proyecto. */
    repo: 'https://github.com/Pausiar/openfy',
    /** Versión publicada, usada como respaldo si el manifiesto no se puede leer. */
    fallbackVersion: '0.2.2',
    /** Fecha de la última revisión de los textos legales. */
    legalUpdated: '2026-10-23',
  },
};
