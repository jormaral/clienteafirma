/* Copyright (C) 2011 [Gobierno de Espana]
 * This file is part of "Cliente @Firma".
 * "Cliente @Firma" is free software; you can redistribute it and/or modify it under the terms of:
 *   - the GNU General Public License as published by the Free Software Foundation;
 *     either version 2 of the License, or (at your option) any later version.
 *   - or The European Software License; either version 1.1 or (at your option) any later version.
 * Date: 11/01/11
 * You may contact the copyright holder at: soporte.afirma5@mpt.es
 */

package es.gob.afirma.keystores.main.common;

import java.io.File;
import java.security.AccessController;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.PrivilegedAction;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.security.auth.callback.PasswordCallback;

import es.gob.afirma.core.AOCancelledOperationException;
import es.gob.afirma.core.misc.AOUtil;
import es.gob.afirma.core.misc.Platform;
import es.gob.afirma.core.ui.AOUIFactory;
import es.gob.afirma.core.ui.NameCertificateBean;
import es.gob.afirma.keystores.main.callbacks.CachePasswordCallback;
import es.gob.afirma.keystores.main.callbacks.NullPasswordCallback;
import es.gob.afirma.keystores.main.callbacks.UIPasswordCallback;
import es.gob.afirma.keystores.main.filters.CertificateFilter;

/** Utilidades para le manejo de almacenes de claves y certificados. */
public final class KeyStoreUtilities {

    private KeyStoreUtilities() {
        // No permitimos la instanciacion
    }

    static final Logger LOGGER = Logger.getLogger("es.gob.afirma"); //$NON-NLS-1$

    private static final String OPENSC_USR_LIB_LINUX = "/usr/lib/opensc-pkcs11.so"; //$NON-NLS-1$

    /** Nombre de los ficheros de biblioteca de los controladores de la FNMT para DNIe y CERES
     * que no tienen implementados el algoritmo SHA1withRSA.
     */
    private static final String[] FNMT_PKCS11_LIBS_WITHOUT_SHA1 = {
    	"DNIe_P11_priv.dll", //$NON-NLS-1$
    	"DNIe_P11_pub.dll", //$NON-NLS-1$
    	"FNMT_P11.dll", //$NON-NLS-1$
    	"UsrPkcs11.dll", //$NON-NLS-1$
    	"UsrPubPkcs11.dll" //$NON-NLS-1$
    };

    /** Crea las l&iacute;neas de configuraci&oacute;n para el proveedor PKCS#11
     * de Sun.
     * @param lib Nombre (con o sin ruta) de la biblioteca PKCS#11
     * @param name Nombre que queremos tenga el proveedor. CUIDADO: SunPKCS11
     *            a&ntilde;ade el prefijo <i>SunPKCS11-</i>.
     * @param slot Lector de tarjetas en el que buscar la biblioteca.
     * @return Fichero con las propiedades de configuracion del proveedor
     *         PKCS#11 de Sun para acceder al KeyStore de un token gen&eacute;rico */
    static String createPKCS11ConfigFile(final String lib, final String name, final Integer slot) {

        final StringBuilder buffer = new StringBuilder("library="); //$NON-NLS-1$

        if (lib.contains(")") || lib.contains("(")) { //$NON-NLS-1$ //$NON-NLS-2$
        	buffer.append(getShort(lib));
        }
        else {
        	buffer.append(lib);
        }
		buffer.append("\r\n") //$NON-NLS-1$

        // Ignoramos la descripcion que se nos proporciona, ya que el
        // proveedor PKCS#11 de Sun
        // falla si llegan espacios o caracteres raros
              .append("name=") //$NON-NLS-1$
              .append(name != null ? name : "AFIRMA-PKCS11") //$NON-NLS-1$
              // El showInfo debe ser false para mantener la compatibilidad con el PKCS#11 de los dispositivos Clauer
              .append("\r\nshowInfo=false\r\n"); //$NON-NLS-1$

        if (slot != null) {
            buffer.append("slot=").append(slot).append("\r\n"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        // Por un problema con la version 10 del driver de la FNMT para el DNIe y tarjetas CERES
        // debemos deshabilitar el mecanismo del algorimto de firma con SHA1 para que lo emule
        for (final String problematicLib : FNMT_PKCS11_LIBS_WITHOUT_SHA1) {
        	if (problematicLib.equalsIgnoreCase(new java.io.File(lib).getName())) {
        		buffer.append("disabledMechanisms={ CKM_SHA1_RSA_PKCS }\r\n"); //$NON-NLS-1$
        		break;
        	}
        }

        LOGGER.info("Creada configuracion PKCS#11:\r\n" + buffer.toString()); //$NON-NLS-1$
        return buffer.toString();
    }

    private static final int ALIAS_MAX_LENGTH = 120;

    /** Obtiene una mapa con las descripciones usuales de los alias de
     * certificados (como claves de estas &uacute;ltimas). Se aplicar&acute;n los
     * filtros de certificados sobre todos ellos y se devolver&acute;n aquellos
     * certificados que cumplan con alguno de los filtros definidos.
     * @param aliases
     *        Alias de los certificados entre los que el usuario debe
     *        seleccionar uno
     * @param ksm
     *        Gestor de los almac&eacute;nes de certificados a los que pertenecen los alias.
     *        Debe ser {@code null} si se quiere usar el m&eacute;todo para seleccionar
     *        otra cosa que no sean certificados X.509 (como claves de cifrado)
     * @param checkPrivateKeys
     *        Indica si se debe comprobar que el certificado tiene clave
     *        privada o no, para no mostrar aquellos que carezcan de ella
     * @param showExpiredCertificates
     *        Indica si se deben o no mostrar los certificados caducados o
     *        aun no v&aacute;lidos
     * @param certFilters
     *        Filtros a aplicar sobre los certificados.
     * @return Alias seleccionado por el usuario */
    public static Map<String, String> getAliasesByFriendlyName(final String[] aliases,
                                                               final AOKeyStoreManager ksm,
                                                               final boolean checkPrivateKeys,
                                                               final boolean showExpiredCertificates,
                                                               final List<CertificateFilter> certFilters) {

        // Creamos un mapa con la relacion Alias-Nombre_a_mostrar de los
        // certificados
    	final String[] trimmedAliases = aliases.clone();
        final Map<String, String> aliassesByFriendlyName = new Hashtable<String, String>(trimmedAliases.length);
        for (final String trimmedAlias : trimmedAliases) {
            aliassesByFriendlyName.put(trimmedAlias, trimmedAlias);
        }

        String tmpCN;
        if (ksm != null && ksm.getKeyStores().size() > 0) {

        	KeyStore ks = null;
        	X509Certificate tmpCert;
            for (final String al : aliassesByFriendlyName.keySet().toArray(new String[aliassesByFriendlyName.size()])) {
                tmpCert = null;

                // Seleccionamos el KeyStore en donde se encuentra el alias
                for (final KeyStore tmpKs : ksm.getKeyStores()) {
                    try {
                        tmpCert = (X509Certificate) tmpKs.getCertificate(al);
                    }
                    catch (final RuntimeException e) {

                    	// Comprobaciones especifica para la compatibilidad con el proveedor de DNIe
                    	if ("es.gob.jmulticard.ui.passwordcallback.CancelledOperationException".equals(e.getClass().getName()) || //$NON-NLS-1$
                    		"es.gob.jmulticard.card.AuthenticationModeLockedException".equals(e.getClass().getName()) || //$NON-NLS-1$
                    		"es.gob.jmulticard.jse.provider.BadPasswordProviderException".equals(e.getClass().getName()) || //$NON-NLS-1$
                    		"es.gob.jmulticard.jse.provider.SignatureAuthException".equals(e.getClass().getName())) { //$NON-NLS-1$
                    			throw e;
                    	}
                        LOGGER.warning("No se ha inicializado el KeyStore indicado: " + e); //$NON-NLS-1$
                        continue;
                    }
                    catch (final Exception e) {
                        LOGGER.warning("No se ha inicializado el KeyStore indicado: " + e); //$NON-NLS-1$
                        continue;
                    }
                    if (tmpCert != null) {
                        ks = tmpKs;
                        break;
                    }
                }

                // Si no tenemos Store para el alias en curso, pasamos al
                // siguiente alias
                if (ks == null) {
                    continue;
                }

                if (tmpCert == null) {
                    LOGGER.warning("El KeyStore no permite extraer el certificado publico para el siguiente alias: " + al); //$NON-NLS-1$
                    continue;
                }

                if (!showExpiredCertificates) {
                    try {
                        tmpCert.checkValidity();
                    }
                    catch (final Exception e) {
                        LOGGER.info(
                            "Se ocultara el certificado '" + al + "' por no ser valido: " + e //$NON-NLS-1$ //$NON-NLS-2$
                        );
                        aliassesByFriendlyName.remove(al);
                        continue;
                    }
                }

                if (checkPrivateKeys) {
                    try {
                        if ("KeychainStore".equals(ks.getType())) { //$NON-NLS-1$
                            final KeyStore tmpKs = ks;
                            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                            	/** {@inheritDoc} */
                                @Override
								public Void run() {
                                    final PrivateKey key;
                                    try {
                                        LOGGER.info("Detectado almacen Llavero de Mac OS X, se trataran directamente las claves privadas"); //$NON-NLS-1$
                                        key = (PrivateKey) tmpKs.getKey(al, "dummy".toCharArray()); //$NON-NLS-1$
                                    }
                                    catch (final Exception e) {
                                        throw new UnsupportedOperationException("No se ha podido recuperar directamente la clave privada en Mac OS X", e); //$NON-NLS-1$
                                    }
                                    if (key == null) {
                                        throw new UnsupportedOperationException("No se ha podido recuperar directamente la clave privada en Mac OS X"); //$NON-NLS-1$
                                    }
                                    return null;
                                }
                            });
                        }
                        else if (!(ks.getEntry(al, new KeyStore.PasswordProtection(new char[0])) instanceof KeyStore.PrivateKeyEntry)) { //TODO: Solo DNIe
                            aliassesByFriendlyName.remove(al);
                            LOGGER.info(
                              "El certificado '" + al + "' no era tipo trusted pero su clave tampoco era de tipo privada, no se mostrara" //$NON-NLS-1$ //$NON-NLS-2$
                            );
                            continue;
                        }
                    }
                    catch (final UnsupportedOperationException e) {
                        aliassesByFriendlyName.remove(al);
                        LOGGER.info(
                          "El certificado '" + al + "' no se mostrara por no soportar operaciones de clave privada" //$NON-NLS-1$ //$NON-NLS-2$
                        );
                    }
                    catch (final Exception e) {
                        LOGGER.info(
                          "Se ha incluido un certificado (" + al + ") con clave privada inaccesible: " + e //$NON-NLS-1$ //$NON-NLS-2$
                        );
                    }
                }
            }

            // Aplicamos los filtros de certificados
            if (certFilters != null && certFilters.size() > 0) {
            	final Map<String, String> filteredAliases = new Hashtable<String, String>();
                for (final CertificateFilter cf : certFilters) {
                	for (final String filteredAlias : cf.matches(aliassesByFriendlyName.keySet().toArray(new String[0]), ksm)) {
                		filteredAliases.put(filteredAlias, aliassesByFriendlyName.get(filteredAlias));
                	}
                }
            	aliassesByFriendlyName.clear();
            	aliassesByFriendlyName.putAll(filteredAliases);
            }

            for (final String alias : aliassesByFriendlyName.keySet().toArray(new String[0])) {
            	tmpCN = AOUtil.getCN(ksm.getCertificate(alias));

            	if (tmpCN != null) {
            		aliassesByFriendlyName.put(alias, tmpCN);
            	}
            	else {
            		// Hacemos un trim() antes de insertar, porque los alias de los
            		// certificados de las tarjetas CERES terminan con un '\r', que se
            		// ve como un caracter extrano
            		aliassesByFriendlyName.put(alias, alias.trim());
            	}
            }

        }

        else {

            // Vamos a ver si en vez de un alias nos llega un Principal X.500 completo,
            // en cuyo caso es muy largo como para mostrase y mostrariamos solo el
            // CN o una version truncada si no nos cuela como X.500.
            // En este bucle usamos la clave tanto como clave como valor porque
            // asi se ha inicializado el mapa.
            for (final String al : aliassesByFriendlyName.keySet().toArray(new String[aliassesByFriendlyName.size()])) {
                final String value = aliassesByFriendlyName.get(al);
                if (value.length() > ALIAS_MAX_LENGTH) {
                    tmpCN = AOUtil.getCN(value);
                    if (tmpCN != null) {
                        aliassesByFriendlyName.put(al, tmpCN);
                    }
                    else {
                        aliassesByFriendlyName.put(al, value.substring(0, ALIAS_MAX_LENGTH - 3) + "..."); //$NON-NLS-1$
                    }
                }
                else {
                    aliassesByFriendlyName.put(al, value.trim());
                }
            }
        }

        return aliassesByFriendlyName;

    }

    /** Muestra un di&aacute;logo para que el usuario seleccione entre los
     * certificados mostrados.
     * @param alias
     *        Alias de los certificados entre los que el usuario debe
     *        seleccionar uno
     * @param ksm
     *        Gestor de los almac&eacute;nes de certificados a los que pertenecen los alias.
     *        Debe ser {@code null} si se quiere usar el m&eacute;todo para seleccionar
     *        otra cosa que no sean certificados X.509 (como claves de cifrado)
     * @param parentComponent
     *        Componente padre (para ls amodalidad)
     * @param checkPrivateKeys
     *        Indica si se debe comprobar que el certificado tiene clave
     *        privada o no, para no mostrar aquellos que carezcan de ella
     * @param checkValidity
     *        Indica si se debe comprobar la validez temporal de un
     *        certificado al ser seleccionado
     * @param showExpiredCertificates
     *        Indica si se deben o no mostrar los certificados caducados o
     *        aun no v&aacute;lidos
     * @return Alias seleccionado por el usuario
     * @throws AOCancelledOperationException
     *         Si el usuario cancela manualmente la operaci&oacute;n
     * @throws AOCertificatesNotFoundException
     *         Si no hay certificados que mostrar al usuario */
    public static String showCertSelectionDialog(final String[] alias,
                                                 final AOKeyStoreManager ksm,
                                                 final Object parentComponent,
                                                 final boolean checkPrivateKeys,
                                                 final boolean checkValidity,
                                                 final boolean showExpiredCertificates) throws AOCertificatesNotFoundException {
        return showCertSelectionDialog(
               alias,
               ksm,
               parentComponent,
               checkPrivateKeys,
               checkValidity,
               showExpiredCertificates,
               null,
               false
        );
    }

    /** Muestra un di&aacute;logo para que el usuario seleccione entre los
     * certificados mostrados. Es posible indicar que s&ocuate;lo puede haber un
     * certificado tras recuperarlos del repositorio y aplicar los filtros, en
     * cuyo caso se seleccionar&iacute; autom&aacute;ticamente. Si se pidiese
     * que se seleccione autom&aacute;ticamemte un certificado y hubiese
     * m&aacute;s de uno, se devolver&iacute;a una excepci&oacute;n.
     * @param alias
     *        Alias de los certificados entre los que el usuario debe
     *        seleccionar uno
     * @param ksm
     *        Gestor de los almac&eacute;nes de certificados a los que pertenecen los alias.
     *        Debe ser {@code null} si se quiere usar el m&eacute;todo para seleccionar
     *        otra cosa que no sean certificados X.509 (como claves de cifrado)
     * @param parentComponent
     *        Componente padre (para la modalidad)
     * @param checkPrivateKeys
     *        Indica si se debe comprobar que el certificado tiene clave
     *        privada o no, para no mostrar aquellos que carezcan de ella
     * @param checkValidity
     *        Indica si se debe comprobar la validez temporal de un
     *        certificado al ser seleccionado
     * @param showExpiredCertificates
     *        Indica si se deben o no mostrar los certificados caducados o
     *        aun no v&aacute;lidos
     * @param certFilters
     *        Filtros sobre los certificados a mostrar
     * @param mandatoryCertificate
     *        Indica si los certificados disponibles (tras aplicar el
     *        filtro) debe ser solo uno.
     * @return Alias seleccionado por el usuario
     * @throws AOCancelledOperationException
     *         Si el usuario cancela manualmente la operaci&oacute;n
     * @throws AOCertificatesNotFoundException
     *         Si no hay certificados que mostrar al usuario */
    public static String showCertSelectionDialog(final String[] alias,
    		final AOKeyStoreManager ksm,
    		final Object parentComponent,
    		final boolean checkPrivateKeys,
    		final boolean checkValidity,
    		final boolean showExpiredCertificates,
    		final List<CertificateFilter> certFilters,
    		final boolean mandatoryCertificate) throws AOCertificatesNotFoundException {

    	if (alias == null && ksm == null || alias != null && alias.length == 0) {
    		throw new AOCertificatesNotFoundException("El almacen no contenia entradas"); //$NON-NLS-1$
    	}

    	final Map<String, String> aliassesByFriendlyName =
    		KeyStoreUtilities.getAliasesByFriendlyName(
				alias != null ? alias : ksm.getAliases(),
				ksm,
				checkPrivateKeys,
				showExpiredCertificates,
				certFilters
			);

    	// Miramos si despues de filtrar las entradas queda alguna o se ha
    	// quedado la lista vacia
    	if (aliassesByFriendlyName.size() == 0) {
    		throw new AOCertificatesNotFoundException("El almacen no contenia entradas validas"); //$NON-NLS-1$
    	}

    	// Si se ha pedido que se seleccione automaticamente un certificado, se
    	// seleccionara
    	// si hay mas de un certificado que se ajuste al filtro, se dara a
    	// elegir
    	if (mandatoryCertificate && aliassesByFriendlyName.size() == 1) {
    			return aliassesByFriendlyName.keySet().toArray()[0].toString();
    	}

    	// Ordenamos el array de alias justo antes de mostrarlo, ignorando entre
    	// mayusculas y minusculas
    	int i = 0;
    	final NameCertificateBean[] orderedFriendlyNames =
    		new NameCertificateBean[aliassesByFriendlyName.size()];
    	for (final String certAlias : aliassesByFriendlyName.keySet().toArray(new String[0])) {
    		orderedFriendlyNames[i++] = new NameCertificateBean(
    				certAlias,
    				aliassesByFriendlyName.get(certAlias),
    				ksm.getCertificate(certAlias));
    	}
    	Arrays.sort(orderedFriendlyNames, new Comparator<NameCertificateBean>() {
    		/** {@inheritDoc} */
    		@Override
			public int compare(final NameCertificateBean o1, final NameCertificateBean o2) {
    			if (o1 == null && o2 == null) {
    				return 0;
    			}
    			else if (o1 == null) {
    				return 1;
    			}
    			else if (o2 == null) {
    				return -1;
    			}
    			else{
    				return o1.getName().compareToIgnoreCase(o2.getName());
    			}
    		}
    	});

    	final String selectedAlias = (String) AOUIFactory.showCertificateSelectionDialog(
    			parentComponent, orderedFriendlyNames);

    	if (selectedAlias == null) {
    		throw new AOCancelledOperationException("Operacion de seleccion de certificado cancelada"); //$NON-NLS-1$
    	}

    	if (checkValidity && ksm != null) {
    		boolean rejected = false;
    		for (final KeyStore ks : ksm.getKeyStores()) {
    			try {
    				if (!ks.containsAlias(selectedAlias)) {
    					continue;
    				}
    			}
    			catch (final Exception e) {
    				continue;
    			}

    			String errorMessage = null;
    			try {
    				((X509Certificate) ks.getCertificate(selectedAlias)).checkValidity();
    			}
    			catch (final CertificateExpiredException e) {
    				errorMessage = KeyStoreMessages.getString("KeyStoreUtilities.2"); //$NON-NLS-1$
    			}
    			catch (final CertificateNotYetValidException e) {
    				errorMessage = KeyStoreMessages.getString("KeyStoreUtilities.3"); //$NON-NLS-1$
    			}
    			catch (final KeyStoreException e) {
    				errorMessage = KeyStoreMessages.getString("KeyStoreUtilities.4"); //$NON-NLS-1$
    			}

    			if (errorMessage != null) {
    				LOGGER.warning("Error durante la validacion: " + errorMessage); //$NON-NLS-1$
    				if (AOUIFactory.showConfirmDialog(
    						parentComponent,
    						errorMessage,
    						KeyStoreMessages.getString("KeyStoreUtilities.5"), //$NON-NLS-1$
    						AOUIFactory.YES_NO_OPTION,
    						AOUIFactory.WARNING_MESSAGE
    				) == AOUIFactory.YES_OPTION) {
    					return selectedAlias;
    				}
    				rejected = true;
    			}

    			if (rejected) {
    				throw new AOCancelledOperationException("Se ha reusado un certificado probablemente no valido"); //$NON-NLS-1$
    			}
    		}
    	}
    	return selectedAlias;
    }

    /** Recupera el PasswordCallback que com&uacute;nmente se requiere para el
     * acceso a un almac&eacute;n de claves.
     * @param kStore Almac&eacuten de claves
     * @param parent Componente sobre el que se deben visualizar los
     *               di&aacute;logos modales (normalmente un <code>java.awt.Comonent</code>)
     * @return Manejador para la solicitud de la clave. */
    public static PasswordCallback getPreferredPCB(final AOKeyStore kStore, final Object parent) {

        if (kStore == null) {
            throw new IllegalArgumentException(
               "No se ha indicado el KeyStore del que desea obtener la PasswordCallBack" //$NON-NLS-1$
            );
        }

        if (AOKeyStore.APPLE.equals(kStore)) {
        	// El almacen KeyChain de Apple exige que se le pase una cadena como contrasena (vale cualquiera)
            return new CachePasswordCallback("dummy".toCharArray()); //$NON-NLS-1$
        }
        if (AOKeyStore.WINDOWS.equals(kStore)) {
            return new NullPasswordCallback();
        }
        return new UIPasswordCallback(KeyStoreMessages.getString("KeyStoreUtilities.6", kStore.getName()), parent); //$NON-NLS-1$
    }

    /** Recupera el manejador de claves asociado a un certificado seg&uacute;n el
     * repositorio en el que se aloja.
     * @param store Almace&eacute;n de claves del certificado.
     * @param parent Componente sobre el que se deben visualizar los
     *               di&aacute;logos modales (normalmente un <code>java.awt.Comonent</code>)
     * @return Manejador para la solicitud de la clave. */
    public static PasswordCallback getCertificatePC(final AOKeyStore store, final Object parent) {
        if (AOKeyStore.WINADDRESSBOOK.equals(store) ||
            AOKeyStore.WINCA.equals(store)          ||
            AOKeyStore.SINGLE.equals(store)         ||
            AOKeyStore.MOZ_UNI.equals(store)        ||
            AOKeyStore.DNIE.equals(store)           ||
            AOKeyStore.PKCS11.equals(store)) {
                return new NullPasswordCallback();
        }
        else if (store == AOKeyStore.DNIEJAVA) {
        	return null;
        }
        else if (AOKeyStore.APPLE.equals(store) || AOKeyStore.WINDOWS.equals(store)) {
        	// El almacen KeyChain de Apple exige que se le pase una cadena como contrasena (vale cualquiera)
        	// Windows cono MiniDriver igualmente exige una contrasena, aunque luego no se comprueba y se pide
        	// con un UI del propio Windows
        	return new CachePasswordCallback("dummy".toCharArray()); //$NON-NLS-1$
        }
        return new UIPasswordCallback(KeyStoreMessages.getString("KeyStoreUtilities.7"), parent); //$NON-NLS-1$
    }

    static String getPKCS11DNIeLib() throws AOKeyStoreManagerException {
        if (Platform.OS.WINDOWS.equals(Platform.getOS())) {
            final String lib = Platform.getSystemLibDir();
            if (new File(lib + "\\DNIe_P11_priv.dll").exists()) { //$NON-NLS-1$
            	return lib + "\\DNIe_P11_priv.dll"; //$NON-NLS-1$
            }
            if (new File(lib + "\\UsrPkcs11.dll").exists()) { //$NON-NLS-1$
                return lib + "\\UsrPkcs11.dll";  //$NON-NLS-1$
            }
            if (new File(lib + "\\opensc-pkcs11.dll").exists()) { //$NON-NLS-1$
                return lib + "\\opensc-pkcs11.dll";  //$NON-NLS-1$
            }
            // No soportamos AutBioPkcs11.dll
            throw new AOKeyStoreManagerException("No hay controlador PKCS#11 de DNIe instalado en este sistema Windows"); //$NON-NLS-1$
        }
        if (Platform.OS.MACOSX.equals(Platform.getOS())) {
            if (new File("/Library/OpenSC/lib/libopensc-dnie.dylib").exists()) { //$NON-NLS-1$
                return "/Library/OpenSC/lib/libopensc-dnie.dylib";  //$NON-NLS-1$
            }
            if (new File("/Library/OpenSC/lib/opensc-pkcs11.so").exists()) { //$NON-NLS-1$
                return "/Library/OpenSC/lib/opensc-pkcs11.so"; //$NON-NLS-1$
            }
            if (new File("/Library/OpenSC/lib/libopensc-dnie.1.0.3.dylib").exists()) { //$NON-NLS-1$
                return "/Library/OpenSC/lib/libopensc-dnie.1.0.3.dylib";  //$NON-NLS-1$
            }
            if (new File(OPENSC_USR_LIB_LINUX).exists()) {
                return OPENSC_USR_LIB_LINUX;
            }
            throw new AOKeyStoreManagerException("No hay controlador PKCS#11 de DNIe instalado en este sistema Mac OS X"); //$NON-NLS-1$
        }
        if (new File("/usr/local/lib/libopensc-dnie.so").exists()) { //$NON-NLS-1$
            return "/usr/local/lib/libopensc-dnie.so"; //$NON-NLS-1$
        }
        if (new File("/usr/lib/libopensc-dnie.so").exists()) { //$NON-NLS-1$
            return "/usr/lib/libopensc-dnie.so"; //$NON-NLS-1$
        }
        if (new File("/lib/libopensc-dnie.so").exists()) { //$NON-NLS-1$
            return "/lib/libopensc-dnie.so"; //$NON-NLS-1$
        }
        if (new File(OPENSC_USR_LIB_LINUX).exists()) {
            return OPENSC_USR_LIB_LINUX;
        }
        if (new File("/lib/opensc-pkcs11.so").exists()) { //$NON-NLS-1$
            return "/lib/opensc-pkcs11.so";  //$NON-NLS-1$
        }
        if (new File("/usr/local/lib/opensc-pkcs11.so").exists()) { //$NON-NLS-1$
            return "/usr/local/lib/opensc-pkcs11.so"; //$NON-NLS-1$
        }
        throw new AOKeyStoreManagerException("No hay controlador PKCS#11 de DNIe instalado en este sistema"); //$NON-NLS-1$
    }

	/** Obtiene el nombre corto (8+3) de un fichero o directorio indicado (con ruta).
	 * @param originalPath Ruta completa hacia el fichero o directorio que queremos pasar a nombre corto.
	 * @return Nombre corto del fichero o directorio con su ruta completa, o la cadena originalmente indicada si no puede
	 *         obtenerse la versi&oacute;n corta */
	public static String getShort(final String originalPath) {
		if (originalPath == null || !Platform.OS.WINDOWS.equals(Platform.getOS())) {
			return originalPath;
		}
		final File dir = new File(originalPath);
		if (!dir.exists()) {
			return originalPath;
		}
		try {
			final Process p = new ProcessBuilder(
					"cmd.exe", "/c", "for %f in (\"" + originalPath + "\") do @echo %~sf" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			).start();
			return new String(AOUtil.getDataFromInputStream(p.getInputStream())).trim();
		}
		catch(final Exception e) {
			LOGGER.warning("No se ha podido obtener el nombre corto de " + originalPath + ": " + e); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return originalPath;
	}
}
