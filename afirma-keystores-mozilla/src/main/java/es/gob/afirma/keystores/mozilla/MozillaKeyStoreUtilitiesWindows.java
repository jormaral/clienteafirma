package es.gob.afirma.keystores.mozilla;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Logger;

import es.gob.afirma.core.AOException;
import es.gob.afirma.core.misc.AOUtil;
import es.gob.afirma.core.misc.Platform;
import es.gob.afirma.keystores.main.common.KeyStoreUtilities;

final class MozillaKeyStoreUtilitiesWindows {

	private static final Logger LOGGER = Logger.getLogger("es.gob.afirma"); //$NON-NLS-1$

	private static final String P11_CONFIG_VALID_CHARS = ":\\0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_.\u007E"; //$NON-NLS-1$

	// Bibliotecas Windows de Firefox
	private static final String SOFTOKN3_DLL = "softokn3.dll"; //$NON-NLS-1$
	private static final String PLC4_DLL = "plc4.dll"; //$NON-NLS-1$
	private static final String PLDS4_DLL = "plds4.dll"; //$NON-NLS-1$
	private static final String NSPR4_DLL = "nspr4.dll"; //$NON-NLS-1$
	private static final String MOZSQLITE3_DLL = "mozsqlite3.dll"; //$NON-NLS-1$
	private static final String MOZCRT19_DLL = "mozcrt19.dll"; //$NON-NLS-1$
	private static final String NSSUTIL3_DLL = "nssutil3.dll"; //$NON-NLS-1$
	private static final String FREEBL3_DLL = "freebl3.dll"; //$NON-NLS-1$
	private static final String NSSDBM3_DLL = "nssdbm3.dll";  //$NON-NLS-1$
	private static final String SQLITE3_DLL = "sqlite3.dll"; //$NON-NLS-1$

	// Novedades de Firefox 9
	// IMPORTANTE:
	// No se puede cargar el entorno de ejecucion de Visual C++ 8 ("msvcr80.dll") porque requiere
	// que el EXE de carga tenga empotrado un MANIFEST adecuado
	private static final String MOZUTILS_DLL = "mozutils.dll"; //$NON-NLS-1$

	// Novedades de Firefox 11
	private static final String MOZGLUE_DLL = "mozglue.dll"; //$NON-NLS-1$

	// Firefox x
	private static final String NSS3_DLL = "nss3.dll"; //$NON-NLS-1$



	private MozillaKeyStoreUtilitiesWindows() {
		// No permitimos la instanciacion
	}

	static String getMozillaUserProfileDirectoryWindows() {
		File regFile;

		String finalDir;
		// En Firefox usamos el profiles.ini (el registro clasico esta ya obsoleto)
		regFile = new File(getWindowsAppDataDir(), "Mozilla\\Firefox\\profiles.ini"); //$NON-NLS-1$
		try {
			if (regFile.exists()) {
				finalDir = NSPreferences.getFireFoxUserProfileDirectory(regFile);
			}
			else {
				LOGGER.severe(
					"No se ha encontrado el perfil de usuario de Mozilla en su directorio por defecto, se devolvera null" //$NON-NLS-1$
				);
				return null;
			}
		}
		catch (final Exception e) {
			LOGGER.severe(
				"Error analizando el 'profiles.ini' de usuario de Firefox, se devolvera null: " + e //$NON-NLS-1$
			);
			return null;
		}

		for (final char c : finalDir.toCharArray()) {
			if (P11_CONFIG_VALID_CHARS.indexOf(c) == -1) {
				finalDir = finalDir.replace(Platform.getUserHome(), KeyStoreUtilities.getShort(Platform.getUserHome()));
				break;
			}
		}
		return finalDir.replace('\\', '/');

	}

	static String getSystemNSSLibDirWindows() throws IOException {

		String dir = MozillaKeyStoreUtilities.getNssPathFromCompatibilityFile();

		if (dir == null) {
			throw new FileNotFoundException("No se encuentra el dierctorio de NSS en Windows"); //$NON-NLS-1$
		}

		// Tenemos la ruta del NSS, comprobamos adecuacion por bugs de Java
		boolean illegalChars = false;
		for (final char c : dir.toCharArray()) {
			if (P11_CONFIG_VALID_CHARS.indexOf(c) == -1) {
				illegalChars = true;
				break;
			}
		}

		// Cuidado, el caracter "tilde" (unicode 007E) es valido para perfil de usuario pero no
		// para bibliotecas en java inferior a 6u30
		if (illegalChars) {

			// Tenemos una ruta con caracteres ilegales para la
			// configuracion de SunPKCS#11 por el bug 6581254:
			// http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6581254
			try {

				// Copiamos las DLL necesarias a un temporal y devolvemos el temporal
				final File tmp;
				// Intentamos usar antes el temporal del sistema, para evitar el del usuario, que puede tener caracteres especiales
				final File tmpDir = new File(new File(Platform.getSystemLibDir()).getParent(), "Temp"); //$NON-NLS-1$
				if (tmpDir.isDirectory() && tmpDir.canWrite() && tmpDir.canRead()) {
					tmp = File.createTempFile("nss", null, tmpDir); //$NON-NLS-1$
				}
				else {
					tmp = File.createTempFile("nss", null); //$NON-NLS-1$
				}
				tmp.delete();
				if (!tmp.mkdir()) {
					throw new AOException("No se ha creado el directorio temporal"); //$NON-NLS-1$
				}
				
				copyFile(new String[] {
					SOFTOKN3_DLL,   // "softokn3" es comun para todos los Firefox a partir del 2
					MOZSQLITE3_DLL, // En Firefox 4 sqlite3.dll pasa a llamarse mozsqlite3.dll
					SQLITE3_DLL,
					NSS3_DLL,
					PLDS4_DLL,
					NSPR4_DLL,
					PLC4_DLL,
					MOZCRT19_DLL,
					NSSUTIL3_DLL,
					FREEBL3_DLL,
					NSSDBM3_DLL,
					MOZUTILS_DLL,
					MOZGLUE_DLL     // A partir de Firefox 11
				}, dir, tmp.getCanonicalPath());

				dir = tmp.getCanonicalPath();

			}
			catch (final Exception e) {
				LOGGER.warning("No se ha podido duplicar NSS en un directorio temporal, si esta version de JRE esta afectada por el error 6581254 de Java es posible que no pueda cargarse: " + e); //$NON-NLS-1$
			}

		}

		if (dir != null) {
			return dir;
		}

		throw new FileNotFoundException("No se ha encontrado un NSS compatible en Windows"); //$NON-NLS-1$

	}

	/** Recupera el listado de dependencias de la biblioteca "softkn3.dll" para el
	 * sistema operativo Windows. Los nombres apareceran ordenados de tal forma las
	 * bibliotecas no tengan dependencias de otra que no haya aparecido
	 * anterioremente en la lista.
	 * @param nssPath Ruta al directorio de NSS (terminado en barra).
	 * @return Listado con los nombres de las bibliotecas. */
	static String[] getSoftkn3DependenciesWindows(final String nssPath) {
		return new String[] {
			nssPath + MOZGLUE_DLL,    // Firefox 11
			nssPath + NSS3_DLL,       // Firefox 24
			nssPath + MOZUTILS_DLL,   // Firefox 9 y 10
			nssPath + MOZCRT19_DLL,   // Firefox desde 3 hasta 8
			nssPath + NSPR4_DLL,      // Firefox 2 y superior
			nssPath + PLDS4_DLL,      // Firefox 2 y superior
			nssPath + PLC4_DLL,       // Firefox 2 y superior
			nssPath + NSSUTIL3_DLL,   // Firefox 3 y superior
			nssPath + MOZSQLITE3_DLL, // Firefox 4 y superior
			nssPath + SQLITE3_DLL,    // Firefox 3
			nssPath + NSSDBM3_DLL,    // Firefox 3 y superior
			nssPath + FREEBL3_DLL     // Firefox 3 y superior
		};
	}

	static String getSoftoknLibNameWindows() {
		return SOFTOKN3_DLL;
	}

	private static String appData = null;

	private static String getWindowsAppDataDir() {

		// Miramos primero con la variable de entorno
		if (appData == null) {
			final String ret = System.getenv("AppData"); //$NON-NLS-1$
			if (ret != null) {
				LOGGER.info(
					"Se ha comprobado la situacion del directorio 'AppData' de Windows a traves de la variable de entorno" //$NON-NLS-1$
				);
				appData = ret;
			}
		}
		if (appData != null) {
			return appData;
		}

		// Y por ultimo con el directorio por defecto de Windows 7 y Windows 8
		final String probablyPath = "C:\\Users\\" + System.getProperty("user.name") + "\\AppData\\Roaming"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		final File f = new File(probablyPath);
		if (f.exists() && f.isDirectory()) {
			appData = probablyPath;
			LOGGER.info(
				"Se ha comprobado la situacion del directorio 'AppData' de Windows manualmente" //$NON-NLS-1$
			);
			return appData;
		}

		appData = null;
		throw new IllegalStateException("No se ha podido determinar la situacion del directorio 'AppData' de Windows"); //$NON-NLS-1$

	}

	/** Copia ficheros de un directorio a otro, ignorando los ficheros que no existan.
	 * @param fileNames Nombres de los ficheros a copiar
	 * @param sourceDir Directorio de origen, no debe tener la barra al final
	 * @param destDir Directorio de destino, debe tener la barra al final */
	private static void copyFile(final String[] fileNames, final String sourceDir, final String destDir) {
		if (fileNames !=null) {
			File tmpFile;
			for(final String f : fileNames) {
				tmpFile = new File(sourceDir, f);
				if (tmpFile.exists()) {
					try {
						AOUtil.copyFile(tmpFile, new File(destDir, f));
					}
					catch (final IOException e) {
						LOGGER.warning("No se ha podido copiar '" + f + "' a '" + destDir + "': " + e); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					}
				}
			}
		}
	}

}
