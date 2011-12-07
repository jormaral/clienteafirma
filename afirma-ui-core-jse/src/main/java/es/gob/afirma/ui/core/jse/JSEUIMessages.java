/*******************************************************************************
 * Este fichero forma parte del Cliente @firma.
 * El Cliente @firma es un aplicativo de libre distribucion cuyo codigo fuente puede ser consultado
 * y descargado desde http://forja-ctt.administracionelectronica.gob.es/
 * Copyright 2009,2010,2011 Gobierno de Espana
 * Este fichero se distribuye bajo  bajo licencia GPL version 2  segun las
 * condiciones que figuran en el fichero 'licence' que se acompana. Si se distribuyera este
 * fichero individualmente, deben incluirse aqui las condiciones expresadas alli.
 ******************************************************************************/

package es.gob.afirma.ui.core.jse;

import java.util.Locale;
import java.util.ResourceBundle;

import es.gob.afirma.core.misc.AOUtil;

/** Clase para la obtencion de los recursos textuales del UI del n&uacute;cleo del
 * cliente Afirma. */
final class JSEUIMessages {

    private static final String BUNDLE_NAME = "uimessages"; //$NON-NLS-1$
    private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME, Locale.getDefault(), AOUtil.getCleanClassLoader());

    private JSEUIMessages() {
        // No permitimos la instanciacion
    }

    /** Recupera el texto identificado con la clave proporcionada.
     * @param key
     *        Clave del texto.
     * @return Recuerso textual. */
    static String getString(final String key) {
        try {
            return RESOURCE_BUNDLE.getString(key);
        }
        catch (final Exception e) {
            return '!' + key + '!';
        }
    }

    /** Recupera el texto identificado con la clave proporcionada y sustituye la
     * subcadenas "%0" por el texto proporcionado.
     * @param key
     *        Clave del texto.
     * @param text
     *        Texto que se desea insertar.
     * @return Recuerso textual con la subcadena sustituida. */
    static String getString(final String key, final String text) {
        try {
            return RESOURCE_BUNDLE.getString(key).replace("%0", text); //$NON-NLS-1$
        }
        catch (final Exception e) {
            return '!' + key + '!';
        }
    }

//    /** Recupera el texto identificado con la clave proporcionada y sustituye las
//     * subcadenas de tipo "%i" por el texto en la posici&oacute;n 'i' del array
//     * proporcionado.
//     * @param key
//     *        Clave del texto.
//     * @param params
//     *        Par&aacute;metros que se desean insertar.
//     * @return Recuerso textual con las subcadenas sustituidas. */
//    static String getString(final String key, final String[] params) {
//
//        String text;
//        try {
//            text = RESOURCE_BUNDLE.getString(key);
//        }
//        catch (final Exception e) {
//            return '!' + key + '!';
//        }
//
//        if (params != null) {
//            for (int i = 0; i < params.length; i++) {
//                text = text.replace("%" + i, params[i]); //$NON-NLS-1$
//            }
//        }
//
//        return text;
//    }
}