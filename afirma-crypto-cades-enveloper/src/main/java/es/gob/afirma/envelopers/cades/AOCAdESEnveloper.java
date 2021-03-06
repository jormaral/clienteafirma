/* Copyright (C) 2011 [Gobierno de Espana]
 * This file is part of "Cliente @Firma".
 * "Cliente @Firma" is free software; you can redistribute it and/or modify it under the terms of:
 *   - the GNU General Public License as published by the Free Software Foundation;
 *     either version 2 of the License, or (at your option) any later version.
 *   - or The European Software License; either version 1.1 or (at your option) any later version.
 * Date: 11/01/11
 * You may contact the copyright holder at: soporte.afirma5@mpt.es
 */

package es.gob.afirma.envelopers.cades;

import java.security.InvalidKeyException;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.cert.X509Certificate;
import java.util.Properties;

import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;

import es.gob.afirma.core.AOException;
import es.gob.afirma.core.ciphers.AOCipherConfig;
import es.gob.afirma.core.ciphers.CipherConstants.AOCipherAlgorithm;
import es.gob.afirma.core.ciphers.CipherConstants.AOCipherBlockMode;
import es.gob.afirma.core.ciphers.CipherConstants.AOCipherPadding;
import es.gob.afirma.core.envelopers.AOEnveloper;
import es.gob.afirma.core.signers.AOSignConstants;
import es.gob.afirma.core.signers.AdESPolicy;
import es.gob.afirma.signers.pkcs7.P7ContentSignerParameters;

/** Funcionalidad de sobres digitales con CAdES. */
public class AOCAdESEnveloper implements AOEnveloper {

    /** M&eacute;todo que realiza el resto de firmas permitidas por CADES. Son
     * las siguientes: <br/>
     * <ul>
     * <li>Data</li>
     * <li>Signed Data</li>
     * <li>Digested Data</li>
     * <li>Enveloped Data</li>
     * <li>Signed and Enveloped Data</li>
     * </ul>
     * Para la generaci&oacute;n de la clave interna se utiliza por defecto el
     * AES.
     * En el caso de que sea tipo "Enveloped data" o
     * "Signed and enveloped data", la clave se generar&aacute; usando el
     * algoritmo pasado como par&aacute;metro. Dicha clave se cifrar&aacute;
     * despu&eacute;s con la clave p&uacute;blica del certificado que identifica
     * al usuario destinatario.
     * Nota: El par&aacute;metro algorithm no es el agoritmo de cifrado, es para
     * el digestAlgorithm usado en los "Unsigned Attributes".
     * @param data
     *        Datos a envolver.
     * @param digestAlgorithm
     *        Algoritmo a usar para la firma (SHA1withRSA, MD5withRSA,...)
     * @param type
     *        Tipo de "envelop" que se quiere hacer.
     * @param keyEntry
     *        Clave privada a usar para firmar.
     * @param certDest
     *        Certificados de los usuarios a los que va destinado el sobre
     *        digital.
     * @param cipherAlgorithm
     *        Algoritmo utilizado para cifrar
     * @param xParams
     *        Par&aacute;metros adicionales
     * @return Envoltorio CADES.
     * @throws AOException
     *         Cuando ocurre cualquier problema en el proceso. */
    @Override
	public byte[] envelop(final byte[] data,
                          final String digestAlgorithm,
                          final String type,
                          final PrivateKeyEntry keyEntry,
                          final X509Certificate[] certDest,
                          final AOCipherAlgorithm cipherAlgorithm,
                          final String datTyp,
                          final Properties xParams) throws AOException {

        final Properties extraParams = xParams !=null ? xParams : new Properties();

        // Comprobamos que el archivo a tratar no sea nulo.
        if (data == null) {
            throw new IllegalArgumentException("El archivo a tratar no puede ser nulo."); //$NON-NLS-1$
        }

        final P7ContentSignerParameters csp = new P7ContentSignerParameters(data, digestAlgorithm);

        // tipos de datos a firmar.
        final String dataType = datTyp != null ? datTyp : PKCSObjectIdentifiers.data.getId();

        // Datos firmados.
        byte[] dataSigned = null;

        // Seleccion del algoritmo de cifrado.
        AOCipherConfig config = null;
        if (cipherAlgorithm == null) {
            // Por defecto usamos el AES.
            config = new AOCipherConfig(AOCipherAlgorithm.AES, AOCipherBlockMode.CBC, AOCipherPadding.PKCS5PADDING);
        }
        /*
         * En caso de usar un algoritmo de cifrado, si no funciona es porque el
         * Provider no lo soporta.
         */
        else {
            config = new AOCipherConfig(cipherAlgorithm, AOCipherBlockMode.CBC, AOCipherPadding.PKCS5PADDING);
        }

        try {
            // Busqueda del tipo que nos han solicitado.

            // Es Data.
            if (AOSignConstants.CMS_CONTENTTYPE_DATA.equals(type)) {
				dataSigned = CAdESData.genData(csp);
            }
            // Es Digested Data.
            else if (AOSignConstants.CMS_CONTENTTYPE_DIGESTEDDATA.equals(type)) {
				dataSigned = CAdESDigestedData.genDigestedData(csp, dataType);
            }
            // Es Enveloped Data. El null y el vacio se consideran Enveloped Data, el por defecto
            else if (AOSignConstants.CMS_CONTENTTYPE_ENVELOPEDDATA.equals(type)  || type == null || "".equals(type)) { //$NON-NLS-1$
                if (keyEntry != null) {
                    dataSigned = new CAdESEnvelopedData().genEnvelopedData(
                		csp,
                		(X509Certificate[]) keyEntry.getCertificateChain(),
                		config,
                		certDest,
                		dataType
            		);
                }
                else {
                    dataSigned = new CAdESEnvelopedData().genEnvelopedData(data, digestAlgorithm, config, certDest, dataType);
                }
            }
            // Es Signed and Enveloped Data.
            else {
                dataSigned = new CAdESEPESSignedAndEnvelopedData().genCADESEPESSignedAndEnvelopedData(
            		csp,
            		(X509Certificate[]) keyEntry.getCertificateChain(),
                    config,
                    new AdESPolicy(extraParams),
                    certDest,
                    PKCSObjectIdentifiers.signedData.getId(),
                    keyEntry
                );
            }
        }
        catch (final Exception e) {
            throw new AOException("Error generando el enveloped de CADES", e); //$NON-NLS-1$
        }

        return dataSigned;
    }


    /** Cifra un contenido (t&iacute;picamente un fichero) usando para ello una
     * contrase&ntilde;a.<br/>
     * Los algoritmos y modos de firma disponibles se declaran en AOSignConstants.<br/>
     * Se usar&aacute; por defecto el algoritmo de cifrado "AES".
     * La clave usada para cifrar el contenido puede ser tanto un password como
     * una clave privada del usuario codificada.
     * En el caso de que sea una clave codificada en base 64, se usar&aacute;
     * como algoritmos los tipo AES, DES ... En el caso de que sea un password,
     * se usar&aacute; un algoritmo de tipo PBE.
     * Nota: El par&aacute;metro algorithm no es el agoritmo de cifrado, es para
     * el digestAlgorithm usado en los "Unsigned Attributes".
     * @param data
     *        Datos a encriptar.
     * @param digestAlgorithm
     *        Algoritmo a usar para la firma (SHA1withRSA, MD5withRSA,...)
     * @param key
     *        Puede ser una clave codificada o una contrase&ntilde;a usada
     *        para cifrar el contenido.
     * @param cipherAlgorithm
     *        Algoritmo a usar para los cifrados
     * @param dataType OID del tipo de datos a encriptar
     * @return Contenido firmado
     * @throws AOException
     *         Cuando ocurre cualquier problema durante el proceso */
    @Override
	public byte[] encrypt(final byte[] data, final String digestAlgorithm, final String key, final AOCipherAlgorithm cipherAlgorithm, final String dataType) throws AOException {

        // Comprobamos que el archivo a cifrar no sea nulo.
        if (data == null) {
            throw new IllegalArgumentException("El archivo a cifrar no puede ser nulo."); //$NON-NLS-1$
        }

        // Seleccion del algoritmo de cifrado.
        AOCipherConfig config = null;
        if (cipherAlgorithm == null) {
            // Por defecto usamos el PBEWITHSHA1ANDDESEDE. El AES en este caso
            // no funciona.
            config = new AOCipherConfig(AOCipherAlgorithm.AES, AOCipherBlockMode.CBC, AOCipherPadding.PKCS5PADDING);
        }
        /*
         * En caso de usar un algoritmo de cifrado, si no funciona es porque el
         * Provider no lo soporta.
         */
        else {
            config = new AOCipherConfig(cipherAlgorithm, AOCipherBlockMode.CBC, AOCipherPadding.PKCS5PADDING);
        }

        try {
			return CAdESEncryptedData.genEncryptedData(data, digestAlgorithm, config, key, dataType != null ? dataType : PKCSObjectIdentifiers.data.getId());
        }
        catch (final Exception e) {
            throw new AOException("Error generando el enveloped de CADES", e); //$NON-NLS-1$
        }

    }


	@Override
	public byte[] recoverData(final byte[] envelop, final PrivateKeyEntry addresseePke)
			throws InvalidKeyException, AOException {

		//TODO: Implementar la apetura de envoltorios CAdES
		throw new UnsupportedOperationException("No se soporta la apertura de envoltorios CAdES"); //$NON-NLS-1$
	}

}
