/* Copyright (C) 2011 [Gobierno de Espana]
 * This file is part of "Cliente @Firma".
 * "Cliente @Firma" is free software; you can redistribute it and/or modify it under the terms of:
 *   - the GNU General Public License as published by the Free Software Foundation;
 *     either version 2 of the License, or (at your option) any later version.
 *   - or The European Software License; either version 1.1 or (at your option) any later version.
 * Date: 11/01/11
 * You may contact the copyright holder at: soporte.afirma5@mpt.es
 */

package es.gob.afirma.signers.ooxml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import es.gob.afirma.core.AOException;
import es.gob.afirma.core.AOFormatFileException;
import es.gob.afirma.core.AOInvalidFormatException;
import es.gob.afirma.core.misc.AOFileUtils;
import es.gob.afirma.core.misc.OfficeAnalizer;
import es.gob.afirma.core.signers.AOSignConstants;
import es.gob.afirma.core.signers.AOSignInfo;
import es.gob.afirma.core.signers.AOSigner;
import es.gob.afirma.core.signers.CounterSignTarget;
import es.gob.afirma.core.util.tree.AOTreeModel;
import es.gob.afirma.core.util.tree.AOTreeNode;
import es.gob.afirma.signers.ooxml.be.fedict.eid.applet.service.signer.AbstractOOXMLSignatureServiceContainer;
import es.gob.afirma.signers.xmldsig.AOXMLDSigSigner;

/** Manejador de firmas electr&oacute;nicas XML de documentos OOXML de Microsoft Office. */
public final class AOOOXMLSigner implements AOSigner {

    static final Logger LOGGER = Logger.getLogger("es.gob.afirma"); //$NON-NLS-1$

    static {
        if (Security.getProvider("XMLDSig") == null) { //$NON-NLS-1$
            try {
                Security.addProvider((Provider) Class.forName("org.jcp.xml.dsig.internal.dom.XMLDSigRI").newInstance()); //$NON-NLS-1$
            }
            catch (final Exception e) {
                LOGGER.warning("No se ha podido agregar el proveedor de firma XMLDSig necesario para firmas XML: " + e); //$NON-NLS-1$
            }
        }
    }

    /** Si la entrada es un documento OOXML, devuelve el mismo documento sin ninguna modificaci&oacute;n.
     * @param sign Documento OOXML
     * @return Documento de entrada si este es OOXML, <code>null</code> en cualquier otro caso
     * @throws IOException Cuando hay errores en la lectura de la firma */
    @Override
	public byte[] getData(final byte[] sign) throws AOException, IOException {

        // Si no es una firma OOXML valida, lanzamos una excepcion
        if (!isSign(sign)) {
            throw new AOInvalidFormatException("El documento introducido no contiene una firma valida"); //$NON-NLS-1$
        }

        // TODO: Por ahora, devolveremos el propio OOXML firmado.
        return sign;
    }

    /** Comprueba que unos datos se adecuen a la estructura b&aacute;sica de un
     * documento OOXML.
     * @param data Datos que deseamos analizar
     * @return {@code true} si el documento es un OOXML, {@code false} en caso
     *         contrario */
    private static boolean isOOXMLFile(final byte[] data) {

        final ZipFile zipFile;
        try {
            zipFile = AOFileUtils.createTempZipFile(data);
            final boolean ret = zipFile.getEntry("[Content_Types].xml") != null && (zipFile.getEntry("_rels/.rels") != null || zipFile.getEntry("_rels\\.rels") != null) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    && (zipFile.getEntry("docProps/app.xml") != null || zipFile.getEntry("docProps\\app.xml") != null) //$NON-NLS-1$ //$NON-NLS-2$
                    && (zipFile.getEntry("docProps/core.xml") != null || zipFile.getEntry("docProps\\core.xml") != null); //$NON-NLS-1$ //$NON-NLS-2$
            zipFile.close();
            return ret;
        }
        catch (final ZipException e) {
            // El fichero no era un ZIP, y por lo tanto, tampoco un OOXML
            return false;
        }
        catch (final Exception e) {
            LOGGER.severe("Error al cargar el fichero OOXML: " + e); //$NON-NLS-1$
            return false;
        }

        // Comprobamos si estan todos los ficheros principales del documento



    }

    /** { {@inheritDoc} */
    @Override
	public AOSignInfo getSignInfo(final byte[] sign) throws AOException, IOException {
        if (sign == null) {
            throw new IllegalArgumentException("No se han introducido datos para analizar"); //$NON-NLS-1$
        }

        if (!isSign(sign)) {
            throw new AOFormatFileException("Los datos introducidos no se corresponden con documento OOXML"); //$NON-NLS-1$
        }

        // Aqui vendria el analisis de la firma buscando alguno de los otros
        // datos de relevancia
        // que se almacenan en el objeto AOSignInfo

        return new AOSignInfo(AOSignConstants.SIGN_FORMAT_OOXML);
    }

    /** { {@inheritDoc} */
    @Override
	public String getSignedName(final String originalName, final String inText) {
        final String inTextInt = inText != null ? inText : ""; //$NON-NLS-1$
        if (originalName == null) {
            return inTextInt + ".ooxml"; //$NON-NLS-1$
        }
        final String originalNameLC = originalName.toLowerCase();
        if (originalNameLC.length() <= 4) {
            return originalName + inTextInt + ".ooxml"; //$NON-NLS-1$
        }
        if (originalNameLC.endsWith(".docx")) { //$NON-NLS-1$
            return originalName.substring(0, originalName.length() - 5) + inTextInt + ".docx"; //$NON-NLS-1$
        }
        if (originalNameLC.endsWith(".xlsx")) { //$NON-NLS-1$
            return originalName.substring(0, originalName.length() - 5) + inTextInt + ".xlsx"; //$NON-NLS-1$
        }
        if (originalNameLC.endsWith(".pptx")) { //$NON-NLS-1$
            return originalName.substring(0, originalName.length() - 5) + inTextInt + ".pptx"; //$NON-NLS-1$
        }
        if (originalNameLC.endsWith(".ppsx")) { //$NON-NLS-1$
            return originalName.substring(0, originalName.length() - 5) + inTextInt + ".ppsx"; //$NON-NLS-1$
        }
        return originalName + inTextInt + ".ooxml"; //$NON-NLS-1$
    }

    /** { {@inheritDoc} */
    @Override
	public AOTreeModel getSignersStructure(final byte[] sign, final boolean asSimpleSignInfo) throws IOException {
        if (sign == null) {
            throw new IllegalArgumentException("Los datos de firma introducidos son nulos"); //$NON-NLS-1$
        }

        if (!isSign(sign)) {
            LOGGER.severe("La firma indicada no es de tipo OOXML"); //$NON-NLS-1$
            return null;
        }

        // Las firmas contenidas en el documento OOXML son de tipo XMLdSig asi
        // que utilizaremos el
        // signer de este tipo para gestionar el arbol de firmas
        final AOSigner xmldsigSigner = new AOXMLDSigSigner();

        // Recuperamos las firmas individuales del documento y creamos el arbol
        final AOTreeNode tree = new AOTreeNode("Datos"); //$NON-NLS-1$
        try {
            for (final byte[] elementSign : OOXMLUtil.getOOXMLSignatures(sign)) {

                // Recuperamos el arbol de firmas de la firma individual. Ya que
                // esta sera una firma simple
                // solo debe contener un nodo de firma. Ignoramos la raiz del
                // arbol, que contiene
                // el ejemplo representativo de los datos firmados y no de la
                // propia firma.
                final AOTreeModel signTree = xmldsigSigner.getSignersStructure(elementSign, asSimpleSignInfo);
                tree.add(((AOTreeNode) signTree.getRoot()).getChildAt(0));
            }
        }
        catch (final Exception e) {
            LOGGER.severe("La estructura de una de las firmas elementales no es valida: " + e); //$NON-NLS-1$
            return null;
        }

        return new AOTreeModel(tree, tree.getChildCount());
    }

    /** Indica si los datos indicados son un documento OOXML susceptible de contener una firma
     * electr&oacute;nica.
     * @param sign Datos que deseamos comprobar.
     * @return Devuelve <code>true</code> si los datos indicados son un documento OOXML susceptible de contener una firma
     * electr&oacute;nica, <code>false</code> en caso contrario.
     * @throws IOException Cuando hay errores en la lectura de la firma */
    @Override
	public boolean isSign(final byte[] sign) throws IOException {
        if (sign == null) {
            LOGGER.warning("Se ha introducido una firma nula para su comprobacion"); //$NON-NLS-1$
            return false;
        }
        return isOOXMLFile(sign) && OOXMLUtil.countOOXMLSignatures(sign) > 0;
    }

    /** Indica si los datos son un documento OOXML susceptible de ser firmado.
     * @param data Datos a comprobar
     * @return <cod>true</code> si los datos son un documento OOXML susceptible de ser firmado, <code>false</code> en caso contrario */
    @Override
	public boolean isValidDataFile(final byte[] data) {
        if (data == null) {
            LOGGER.warning("Se han introducido datos nulos para su comprobacion"); //$NON-NLS-1$
            return false;
        }
        return isOOXMLFile(data);
    }

    /** Agrega una firma electr&oacute;nica a un documento OOXML.
     * @param data Documento OOXML
     * @param algorithm Algoritmo de firma
     * <p>Se aceptan los siguientes algoritmos en el par&aacute;metro <code>algorithm</code>:</p>
     * <ul>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA1withRSA</i></li>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA256withRSA</i></li>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA384withRSA</i></li>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA512withRSA</i></li>
     * </ul>
     * @param key Clave privada del firmante
     * @param certChain Cadena de certificados del firmante
     * @param extraParams No usado, se ignora el valor de este par&aacute;metro
     * @return Documento OOXML firmado
     * @throws AOException Cuando ocurre alg&uacute;n error durante el proceso de firma
     * @throws IOException Cuando hay errores en la lectura de los datos */
    @Override
	public byte[] sign(final byte[] data,
                       final String algorithm,
                       final PrivateKey key,
                       final java.security.cert.Certificate[] certChain,
                       final Properties extraParams) throws AOException, IOException {

        // Comprobamos si es un documento OOXML valido.
        if (!OfficeAnalizer.isOOXMLDocument(data)) {
            throw new AOFormatFileException("Los datos introducidos no se corresponden con un documento OOXML"); //$NON-NLS-1$
        }

        return signOOXML(data, OOXMLUtil.countOOXMLSignatures(data) + 1, algorithm, key, certChain);
    }

    /** Agrega una firma electr&oacute;nica a un documento OOXML.
     * Este m&eacute;todo es completamente equivalente a <code>sign(byte[], String, PrivateKeyEntry, Properties)</code>.
     * @param sign Documento OOXML
     * @param algorithm Algoritmo de firma
     * <p>Se aceptan los siguientes algoritmos en el par&aacute;metro <code>algorithm</code>:</p>
     * <ul>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA1withRSA</i></li>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA256withRSA</i></li>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA384withRSA</i></li>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA512withRSA</i></li>
     * </ul>
     * @param key Clave privada del firmante
     * @param certChain Cadena de certificados del firmante
     * @param extraParams No usado, se ignora el valor de este par&aacute;metro
     * @return Documento OOXML firmado
     * @throws AOException Cuando ocurre alg&uacute;n error durante el proceso de firma
     * @throws IOException Cuando hay errores en la lectura de los datos */
    @Override
	public byte[] cosign(final byte[] sign,
                         final String algorithm,
                         final PrivateKey key,
                         final java.security.cert.Certificate[] certChain,
                         final Properties extraParams) throws AOException, IOException {
    	return sign(sign, algorithm, key, certChain, extraParams);
    }

    /** Agrega una firma electr&oacute;nica a un documento OOXML.
     * Este m&eacute;todo es completamente equivalente a <code>sign(byte[], String, PrivateKeyEntry, Properties)</code>.
     * @param data No usado, se ignora el valor de este par&aacute;metro
     * @param sign Documento OOXML
     * @param algorithm Algoritmo de firma
     * <p>Se aceptan los siguientes algoritmos en el par&aacute;metro <code>algorithm</code>:</p>
     * <ul>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA1withRSA</i></li>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA256withRSA</i></li>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA384withRSA</i></li>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA512withRSA</i></li>
     * </ul>
     * @param key Clave privada del firmante
     * @param certChain Cadena de certificados del firmante
     * @param extraParams No usado, se ignora el valor de este par&aacute;metro
     * @return Documento OOXML firmado
     * @throws AOException Cuando ocurre alg&uacute;n error durante el proceso de firma
     * @throws IOException Cuando hay errores en la lectura de los datos */
    @Override
	public byte[] cosign(final byte[] data,
                         final byte[] sign,
                         final String algorithm,
                         final PrivateKey key,
                         final java.security.cert.Certificate[] certChain,
                         final Properties extraParams) throws AOException, IOException {
    	return cosign(sign, algorithm, key, certChain, extraParams);
    }

    /** M&eacute;todo no implementado. No es posible realizar contrafirmas de
     * documentos OOXML. Lanza una <code>UnsupportedOperationException</code>. */
    @Override
	public byte[] countersign(final byte[] sign,
                              final String algorithm,
                              final CounterSignTarget targetType,
                              final Object[] targets,
                              final PrivateKey key,
                              final java.security.cert.Certificate[] certChain,
                              final Properties extraParams) throws AOException {
        throw new UnsupportedOperationException("No es posible realizar contrafirmas de ficheros OOXML"); //$NON-NLS-1$
    }

    /** Agrega una firma electr&oacute;nica a un documento OOXML.
     * @param ooxmlDocument Documento OOXML.
     * @param signNum N&uacute;mero de la firma que se va a realizar
     * @param algorithm Algoritmo de firma
     * <p>Se aceptan los siguientes algoritmos en el par&aacute;metro <code>algorithm</code>:</p>
     * <ul>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA1withRSA</i></li>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA256withRSA</i></li>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA384withRSA</i></li>
     *  <li>&nbsp;&nbsp;&nbsp;<i>SHA512withRSA</i></li>
     * </ul>
     * @param key Clave privada del firmante
     * @param certChain Cadena de certificados del firmante
     * @return Documento OOXML firmado
     * @throws AOException Cuando ocurre alg&uacute;n error durante el proceso de firma */
    private static byte[] signOOXML(final byte[] ooxmlDocument,
                             final int signNum,
                             final String algorithm,
                             final PrivateKey key,
                             final java.security.cert.Certificate[] certChain) throws AOException {

        // Pasamos la cadena de certificacion a una lista
        if (key == null) {
            throw new AOException("No se ha proporcionado una clave valida"); //$NON-NLS-1$
        }

        try {
			return AbstractOOXMLSignatureServiceContainer.sign(
                 new ByteArrayInputStream(ooxmlDocument),
                 Arrays.asList((X509Certificate[])certChain),
                 AOSignConstants.getDigestAlgorithmName(algorithm),
                 key,
                 signNum
            );
        }
        catch (final Exception e) {
            throw new AOException("Error durante la firma OOXML: " + e, e); //$NON-NLS-1$
        }
    }
}
