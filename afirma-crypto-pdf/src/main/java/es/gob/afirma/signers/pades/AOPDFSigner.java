/* Copyright (C) 2011 [Gobierno de Espana]
 * This file is part of "Cliente @Firma".
 * "Cliente @Firma" is free software; you can redistribute it and/or modify it under the terms of:
 *   - the GNU General Public License as published by the Free Software Foundation;
 *     either version 2 of the License, or (at your option) any later version.
 *   - or The European Software License; either version 1.1 or (at your option) any later version.
 * Date: 11/01/11
 * You may contact the copyright holder at: soporte.afirma5@mpt.es
 */

package es.gob.afirma.signers.pades;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Logger;

import com.lowagie.text.DocumentException;
import com.lowagie.text.exceptions.BadPasswordException;
import com.lowagie.text.pdf.AcroFields;
import com.lowagie.text.pdf.PdfPKCS7;
import com.lowagie.text.pdf.PdfReader;

import es.gob.afirma.core.AOException;
import es.gob.afirma.core.AOInvalidFormatException;
import es.gob.afirma.core.misc.AOUtil;
import es.gob.afirma.core.misc.Platform;
import es.gob.afirma.core.signers.AOSignConstants;
import es.gob.afirma.core.signers.AOSignInfo;
import es.gob.afirma.core.signers.AOSigner;
import es.gob.afirma.core.signers.AOSimpleSignInfo;
import es.gob.afirma.core.signers.CounterSignTarget;
import es.gob.afirma.core.ui.AOUIFactory;
import es.gob.afirma.core.util.tree.AOTreeModel;
import es.gob.afirma.core.util.tree.AOTreeNode;

/** Manejador de firmas binarias de ficheros Adobe PDF en formato PAdES.
 * <p>Para compatibilidad estricta con PAdES-BES/EPES se utiliza <i>ETSI.CAdES.detached</i> como nombre del subfiltro.</p>
 * <p>La compatibilidad con PAdES no es completa, omiti&eacute;ndose los siguientes aspectos de la normativa:</p>
 * <ul>
 *  <li>Firma separada de ficheros empotrados en el documento PDF.</li>
 *  <li>Firma separada de ficheros adjuntos al documento PDF.</li>
 * </ul>
 * <p>
 *  Estas mismas deficiencias provocan igualmente la incompatibilidad de las firmas generadas con "Carpetas PDF" (<i>Portfolios PDF</i>).
 *  Cuando se encuentran documentos PDF con ficheros adjuntos o empotrados se imprime informaci&oacute;n relativa en consola.
 * </p>
 * <p>
 *   Por compatibilidad con Adobe Reader, la firmas se generan con el subfiltro "adbe.pkcs7.detached" en vez de con
 *   "ETSI.CAdES.detached". Consulte la documentaci&oacute;n del par&aacute;metro <code>signatureSubFilter</code> para variar este comportamiento.
 * </p>
 * <p>
 *  La clase necesita espec&iacute;ficamente iText 2.1.7 (no se usan versiones m&aacute;s actuales por cuestiones de licencia) y
 *  BouncyCastle 1.49 o superior (Proveedor + TSP + <i>Mail</i>).
 * </p> */
public final class AOPDFSigner implements AOSigner {

    private static final String PDF_FILE_SUFFIX = ".pdf"; //$NON-NLS-1$
    private static final String PDF_FILE_HEADER = "%PDF-"; //$NON-NLS-1$

    /** Versi&oacute;n de iText necesaria para el uso de esta clase (2.1.7). */
    private static final String ITEXT_VERSION = "2.1.7"; //$NON-NLS-1$

    private static final Logger LOGGER = Logger.getLogger("es.gob.afirma");  //$NON-NLS-1$

    /** Referencia a la &uacute;ltima p&aacute;gina del documento PDF. */
    public static final int LAST_PAGE = -666;

    /** Firma un documento PDF en formato PAdES.
     * <p>
     *  Notas sobre documentos <i>certificados</i>:<br>
     *  Si un PDF firmado se ha certificado (por ejemplo, a&ntilde;adiendo una firma electr&oacute;nica usando Adobe Acrobat), cualquier
     *  modificaci&oacute;n posterior del fichero (como la adici&oacute;n de nuevas firmas con este m&eacute;todo) invalidar&aacute;
     *  las firmas previamente existentes.<br>
     *  Si se detecta un documento PDF certificado, se mostrar&aacute; un di&aacute;logo gr&aacute;fico advirtiendo al usuario de esta
     *  situaci&oacute;n y pidiendo confirmaci&oacute;n para continuar.<br>Si desea evitar interacciones directas con los usuarios
     *  consulte la documentaci&oacute;n de las opciones <code>allowSigningCertifiedPdfs</code> y <code>headLess</code>.<br>
     * </p>
     * <p>
     *  Notas sobre documentos protegidos con contrase&ntilde;a:<br>
     *  Si un PDF est&aacute; protegido con contrase&ntilde;a por estar cifrado, se mostrar&aacute; un di&aacute;logo gr&aacute;fico advirtiendo al usuario de esta
     *  situaci&oacute;n y solicitando la contrase&ntilde;a de apertura del PDF.<br>Si desea evitar interacciones directas con los usuarios
     *  consulte la documentaci&oacute;n de las opciones <code>ownerPassword</code> y <code>headLess</code>.
     *  Adicionalmente, es posible, si el fichero de entrada estaba cifrado y protegido con contrase&ntilde;a, que la salida sea un documento PDF
     *  igualmente cifrado y protegido con contrase&ntilde;a. Consulte la documentaci&oacute;n de la opci&oacute;n <code>avoidEncryptingSignedPdfs</code>
     *  para m&aacute;s informaci&oacute;n.
     * </p>
     * @param data Documento PDF a firmar
     * @param algorithm Algoritmo a usar para la firma.
     * <p>Se aceptan los siguientes algoritmos en el par&aacute;metro <code>algorithm</code>:</p>
     * <ul>
     *  <li><i>SHA1withRSA</i></li>
     *  <li><i>MD5withRSA</i> (no recomendado por vulnerable)</li>
     *  <li><i>MD2withRSA</i> (no recomendado por vulnerable)</li>
     *  <li><i>SHA256withRSA</i></li>
     *  <li><i>SHA384withRSA</i></li>
     *  <li><i>SHA512withRSA</i></li>
     * </ul>
     * @param key Clave privada a usar para firmar
     * @param certChain Cadena de certificados del firmante
     * @param xParams Par&aacute;metros adicionales para la firma.
     * <p>Se aceptan los siguientes valores en el par&aacute;metro <code>xParams</code>:</p>
     * <dl>
     *  <dt><b><i>certificationLevel</i></b></dt>
     *   <dd>
     *    Nivel de certificaci&oacute;n de la firma PDF.<br>Los valores admitidos son num&eacute;ricos, correspondiendo:
     *    <ul>
     *     <li><i>0</i> = Firma ordinaria no certificada (por defecto)</li>
     *     <li><i>1</i> = Firma de autor. No se permite ning&uacute;n cambio posterior en el documento</li>
     *     <li><i>2</i> = Firma de autor certificada para formularios. Se permite &uacute;nicamente el relleno posterior de los campos del formulario</li>
     *     <li><i>3</i> = Firma certificada. Se permite &uacute;nicamente el relleno posterior de los campos del formulario o el a&ntilde;adido de firmas de aprobaci&oacute;n</li>
     *    </ul>
     *   </dd>
     *  <dt><b><i>layer2Text</i></b></dt>
     *   <dd>
     *    Texto a escribir dentro de la "capa 2" de la firma visible. Este texto se escribe &uacute;nicamente si no se ha especificado una
     *    imagen de r&uacute;brica.
     *   </dd>
     *  <dt><b><i>layer2FontFamily</i></b></dt>
     *   <dd>
     *    Tipo de letra a usar en el texto de la "capa 2" de la firma visible. Este par&aacute;metro requiere que se haya establecido tambi&eacute;n
     *    el par&aacute;metro <code>layer2Text</code>.<br>Los valores admitidos son num&eacute;ricos, correspondiendo:
     *    <ul>
     *     <li><i>0</i> = Courier (tipo por defecto)</li>
     *     <li><i>1</i> = Helvetica</li>
     *     <li><i>2</i> = Times Roman</li>
     *     <li><i>3</i> = Symbol</li>
     *     <li><i>4</i> = ZapfDingBats</li>
     *    </ul>
     *   </dd>
     *  <dt><b><i>layer2FontSize</i></b></dt>
     *   <dd>
     *    Tama&ntilde;o de de letra a usar en el texto de la "capa 2" de la firma visible. Este par&aacute;metro requiere que se haya establecido
     *    tambi&eacute;n el par&aacute;metro <code>layer2Text</code>.<br>Los valores admitidos son num&eacute;ricos (y el valor por defecto es 12).
     *   </dd>
     *  <dt><b><i>layer2FontStyle</i></b></dt>
     *   <dd>
     *    Estilo del tipo de letra a usar en el texto de la "capa 2" de la firma visible. Este par&aacute;metro requiere que se haya establecido
     *    tambi&eacute;n el par&aacute;metro <code>layer2Text</code>.<br>Los valores admitidos son num&eacute;ricos, correspondiendo:
     *    <ul>
     *     <li><i>0</i> = Normal (estilo por defecto)</li>
     *     <li><i>1</i> = Negrita</li>
     *     <li><i>2</i> = Cursiva</li>
     *     <li><i>3</i> = Negrita y cursiva</li>
     *     <li><i>4</i> = Subrayado</li>
     *     <li><i>8</i> = Tachado</li>
     *    </ul>
     *    Es posible combinar estilos aplicando la operaci&oacute;n l&oacute;gica <cite>o</cite> sobre los valores num&eacute;ricos a combinar.
     *   </dd>
     *  <dt><b><i>layer2FontColor</i></b></dt>
     *   <dd>
     *    Color del texto de la "capa 2" de la firma visible. Este par&aacute;metro requiere que se haya establecido
     *    tambi&eacute;n el par&aacute;metro <code>layer2Text</code>.<br>Los valores admitidos son textuales (se ignora entre may&uacute;sculas
     *    y min&uacute;sculas), soport&aacute;ndose:
     *    <ul>
     *     <li><i>black</i> = Negro (color por defecto)</li>
     *     <li><i>white</i> = Blanco</li>
     *     <li><i>gray</i> = Gris</li>
     *     <li><i>lightGray</i> = Gris claro</li>
     *     <li><i>darkGray</i> = Gris oscuro</li>
     *     <li><i>red</i> = Rojo</li>
     *     <li><i>pink</i> = Rosa</li>
     *    </ul>
     *   </dd>
     *  <dt><b><i>layer4Text</i></b></dt>
     *   <dd>
     *    Texto a escribir dentro de la "capa 4" de la firma visible. Este texto se escribe &uacute;nicamente si no se ha especificado una
     *    imagen de r&uacute;brica.
     *   </dd>
     *  <dt><b><i>signatureSubFilter</i></b></dt>
     *   <dd>
     *    Nombre del sub-filtro en el diccionario PDF para indicar el tipo de la firma. Si no se indica este par&aacute;metro por defecto se
     *    usa <code>adbe.pkcs7.detached</code> (firma PAdES b&aacute;sica).
     *    Es posible indicar <code>ETSI.CAdES.detached</code> para generar una firma PAdES-BES, si bien el hacerlo puede causar que al a&ntilde;adir firmas adicionales
     *    al PDF se invaliden las ya existentes.
     *   </dd>
     *  <dt><b><i>signatureField</i></b></dt>
     *   <dd>
     *    Nombre del campo en donde insertar la firma.
     *    Si el documento PDF tiene ya un campo de firma precreado es posible utilizarlo para insertar la firma generada, referenci&aacute;ndolo
     *    por su nombre.<br>
     *    Si se indica un nombre de campo de firma que no exista en el documento PDF proporcionado, se generar&aacute; una excepci&oacute;n.
     *   </dd>
     *  <dt><b><i>signatureRubricImage</i></b></dt>
     *   <dd>Imagen JPEG codificada en Base64 de la r&uacute;brica de la firma manuscrita que se desea aparezca como firma visible en el PDF.</dd>
     *  <dt><b><i>signaturePage</i></b></dt>
     *   <dd>
     *    P&aacute;gina del documento PDF donde insertar la firma. Puede usarse la constante <code>LAST_PAGE</code>
     *    para referirse a la &uacute;ltima p&aacute;gina del documento PDF si se desconoce el n&uacute;mero total de
     *    p&aacute;ginas de este.<br>
     *    Este par&aacute;metro se ignora si se ha establecido valor al par&aacute;metro <i>signatureField</i> y necesita que se
     *    establezcan valores v&aacute;lidos a los par&aacute;metros <i>signaturePositionOnPageLowerLeftX</i>, <i>signaturePositionOnPageLowerLeftY</i>,
     *    <i>signaturePositionOnPageUpperRightX</i> y <i>signaturePositionOnPageUpperRightY</i>.
     *   </dd>
     *  <dt><b><i>signaturePositionOnPageLowerLeftX</i></b></dt>
     *   <dd>
     *    Coordenada horizontal inferior izquiera de la posici&oacute;n del recuadro visible de la fimra dentro de la p&aacute;gina.<br>
     *    Es necesario indicar el resto de coordenadas del recuadro mediante los par&aacute;metros <i>signaturePositionOnPageLowerLeftY</i>,
     *    <i>signaturePositionOnPageUpperRightX</i> y <i>signaturePositionOnPageUpperRightY</i>.<br>
     *    Si no se indica una p&aacute;gina en el par&aacute;metro <i>signaturePage</i> la firma se inserta en la &uacute;ltima p&aacute;gina
     *    del documento.
     *   </dd>
     *  <dt><b><i>signaturePositionOnPageLowerLeftY</i></b></dt>
     *   <dd>
     *    Coordenada vertical inferior izquiera de la posici&oacute;n del recuadro visible de la fimra dentro de la p&aacute;gina.<br>
     *    Es necesario indicar el resto de coordenadas del recuadro mediante los par&aacute;metros <i>signaturePositionOnPageLowerLeftX</i>,
     *    <i>signaturePositionOnPageUpperRightX</i> y <i>signaturePositionOnPageUpperRightY</i>.<br>
     *    Si no se indica una p&aacute;gina en el par&aacute;metro <i>signaturePage</i> la firma se inserta en la &uacute;ltima p&aacute;gina
     *    del documento.
     *   </dd>
     *  <dt><b><i>signaturePositionOnPageUpperRightX</i></b></dt>
     *   <dd>
     *    Coordenada horizontal superior derecha de la posici&oacute;n del recuadro visible de la fimra dentro de la p&aacute;gina.<br>
     *    Es necesario indicar el resto de coordenadas del recuadro mediante los par&aacute;metros <i>signaturePositionOnPageLowerLeftX</i>,
     *    <i>signaturePositionOnPageLowerLeftY</i> y <i>signaturePositionOnPageUpperRightY</i>.<br>
     *    Si no se indica una p&aacute;gina en el par&aacute;metro <i>signaturePage</i> la firma se inserta en la &uacute;ltima p&aacute;gina
     *    del documento.
     *   </dd>
     *  <dt><b><i>signaturePositionOnPageUpperRightY</i></b></dt>
     *   <dd>
     *    Coordenada vertical superior derecha de la posici&oacute;n del recuadro visible de la fimra dentro de la p&aacute;gina.<br>
     *    Es necesario indicar el resto de coordenadas del recuadro mediante los par&aacute;metros <i>signaturePositionOnPageLowerLeftX</i>,
     *    <i>signaturePositionOnPageLowerLeftY</i> y <i>signaturePositionOnPageUpperRightX</i>.<br>
     *    Si no se indica una p&aacute;gina en el par&aacute;metro <i>signaturePage</i> la firma se inserta en la &uacute;ltima p&aacute;gina
     *    del documento.
     *   </dd>
     *  <dt><b><i>applySystemDate</i></b></dt>
     *   <dd><code>true</code> si se desea usar la hora y fecha del sistema como hora y fecha de firma, <code>false</code> en caso contrario.
     *  <dt><b><i>signReason</i></b></dt>
     *   <dd>Raz&oacute;n por la que se realiza la firma (este dato se a&ntilde;ade al diccionario PDF, y no a la propia firma).</dd>
     *  <dt><b><i>signatureProductionCity</i></b></dt>
     *   <dd>Ciudad en la que se realiza la firma (este dato se a&ntilde;ade al diccionario PDF, y no a la propia firma).</dd>
     *  <dt><b><i>signerContact</i></b></dt>
     *   <dd>
     *    Contacto del firmante, usualmente una direcci&oacute;n de coreo electr&oacute;nico
     *    (este dato se a&ntilde;ade al diccionario PDF, y no a la propia firma).
     *   </dd>
     *  <dt><b><i>policyIdentifier</i></b></dt>
     *   <dd>
     *    Identificador de la pol&iacute;tica de firma. Debe ser un OID (o una URN de tipo OID) que identifique
     *    &uacute;nivocamente la pol&iacute;tica en formato ASN.1 procesable.
     *   </dd>
     *  <dt><b><i>policyIdentifierHash</i></b></dt>
     *   <dd>
     *    Huella digital del documento de pol&iacute;tica de firma (normalmente del mismo fichero en formato ASN.1 procesable).
     *    Si no se indica una huella digital y el par&aacute;metro <code>policyIdentifier</code> no es una URL accesible
     *    universalmente se usar&aacute; <code>0</code>, mientras que si no se indica una huella digital pero el par&aacute;metro
     *    <code>policyIdentifier</code> es una URL accesible universalmente, se descargara el fichero apuntado por la URL para calcular la huella
     *    digital <i>al vuelo</i>.
     *   </dd>
     *  <dt><b><i>policyIdentifierHashAlgorithm</i></b></dt>
     *   <dd>
     *    Algoritmo usado para el c&aacute;lculo de la huella digital indicada en el par&aacute;metro <code>policyIdentifierHash</code>.
     *    Es obligario indicarlo cuando se proporciona una huella digital distinta de <code>0</code>.
     *   </dd>
     *  <dt><b><i>policyQualifier</i></b></dt>
     *   <dd>
     *    URL que apunta al documento descriptivo de la pol&iacute;tica de firma (normalmente un documento PDF con una descripci&oacute;n textual).
     *   </dd>
     *  <dt><b><i>ownerPassword</i></b></dt>
     *   <dd>
     *    Contrase&ntilde;a de apertura del PDF (contrase&ntilde;a del propietario) si este estaba cifrado.<br>
     *    No se soporta la firma de documentos PDF cifrados con certificados o con algoritmo AES256.
     *   </dd>
     *  <dt><b><i>headLess</i></b></dt>
     *   <dd>
     *    Evita cualquier interacci&oacute;n con el usuario si se establece a <code>true</code>, si no se establece o se establece a <code>false</code>
     *    act&uacute;a normalmente (puede mostrar di&aacute;logos, por ejemplo, para solicitar las contrase&ntilde;as de los PDF cifrados). &Uacute;til para
     *    los procesos desatendidos y por lotes
     *   </dd>
     *  <dt><b><i>avoidEncryptingSignedPdfs</i></b></dt>
     *   <dd>
     *    Si se establece a <code>true</code> no cifra los PDF firmados aunque el original estuviese firmado, si no se establece o se establece a
     *    <code>false</code> los PDF se cifran tras firmarse si el original lo estaba, usando la misma contrase&ntilde;a y opciones que este
     *   </dd>
     *  <dt><b><i>allowSigningCertifiedPdfs</i></b></dt>
     *   <dd>
     *    Si se establece a <code>true</code> permite la firma o cofirma de PDF certificados sin consultarlo al usuario, si se establece a
     *    <code>false</code> o cualquier otro valor se lanza una excepci&oacute;n en caso de intentar firmar o cofirmar un PDF certificado y
     *    si no se establece se mostrar&aacute; un di&aacute;logo al usuario para que confirme que desea realizar la firma a pesar de que
     *    el resultado ser&aacute;n una firma no v&aacute;lida.<br>
     *    <b>Si el par&aacute;metro <code>headLess</code> est&aacute; establecido a <code>true</code>, no podr&aacute; mostrar el di&aacute;logo
     *    de confirmaci&oacute;n as&iacute; que llegados a este punto se lanzar&aacute; una excepci&oacute;n.</b><br>
     *    No se soporta el cifrado de documentos PDF con certificados o con algoritmo AES256.
     *   </dd>
     *  <dt><b><i>tsaURL</i></b></dt>
     *   <dd>URL de la autoridad de sello de tiempo (si no se indica no se a&ntilde;ade sello de tiempo).</dd>
     *  <dt><b><i>tsaPolicy</i></b></dt>
     *   <dd>Pol&iacute;tica de sellado de tiempo (obligatoria si se indica <code>tsaURL</code>).</dd>
     *  <dt><b><i>tsaHashAlgorithm</i></b></dt>
     *   <dd>Algoritmo de huella digital a usar para el sello de tiempo (si no se establece se usa SHA-1).</dd>
     *  <dt><b><i>tsaRequireCert</i></b></dt>
     *   <dd><code>true</code> si se requiere el certificado de la TSA, false en caso contrario (si no se establece se asume <code>true</code>).</dd>
     *  <dt><b><i>tsaUsr</i></b></dt>
     *   <dd>Nombre de usuario de la TSA.</dd>
     *  <dt><b><i>tsaPwd</i></b></dt>
     *   <dd>Contrase&ntilde;a del usuario de la TSA. Se ignora si no de ha establecido adem&aacute;s <code>tsaUsr</code>.</dd>
     *  <dt><b><i>signingCertificateV2</i></b></dt>
     *   <dd>Si se indica a {@code true} se utilizar SigningCertificateV2, si se indica cualquier otra cosa SigningCertificateV1.
     *   Si no se indica nada, se utilizar&aacute; V1 para las firmas SHA1 y V2 para el resto.</dd>
     * </dl>
     * @return Documento PDF firmado en formato PAdES
     * @throws AOException Cuando ocurre cualquier problema durante el proceso */
    @Override
	public byte[] sign(final byte[] data,
			           final String algorithm,
			           final PrivateKey key,
			           final java.security.cert.Certificate[] certChain,
			           final Properties xParams) throws AOException, IOException {

        final Properties extraParams = xParams != null ? xParams : new Properties();

        checkIText();

        try {
            return PAdESSigner.signPDF(key, certChain, data, extraParams, algorithm);
        }
        catch (final  com.lowagie.text.exceptions.InvalidPdfException e) {
        	throw new InvalidPdfException(e);
        }
        catch (final CertificateException e) {
            throw new AOException("Error en el certificado de firma: " + e, e); //$NON-NLS-1$
        }
        catch (final NoSuchAlgorithmException e) {
        	throw new AOException("Error el en algoritmo de firma: " + e, e); //$NON-NLS-1$
		}
        catch (final DocumentException e) {
        	throw new AOException("Error en el tratamiento del PDF: " + e, e); //$NON-NLS-1$
		}
        catch(final PdfIsPasswordProtectedException e) {
        	// El PDF se puede abrir sin contrasena pero la necesita para poder firmarse,
        	// asi que solicitamos una contrasena al usuario
        	// Esto no se hace dentro del propio metodo de firma porque la proteccion se comprueba
        	// al crear la firma, y el documento ya esta abierto sin contrasena
            // Comprobamos que el signer esta en modo interactivo, y si no lo
            // esta no pedimos contrasena por dialogo, principalmente para no interrumpir un firmado por lotes
            // desatendido
            if (Boolean.TRUE.toString().equalsIgnoreCase(extraParams.getProperty("headLess"))) { //$NON-NLS-1$
                throw new BadPdfPasswordException(e);
            }
        	extraParams.put("userPassword", new String(AOUIFactory.getPassword(PDFMessages.getString("AOPDFSigner.0"), null))); //$NON-NLS-1$ //$NON-NLS-2$)
        	return sign(data, algorithm, key, certChain, extraParams);
        }
    }

    /** A&ntilde;ade una firma PAdES a un documento PDF. El comportamiento es exactamente el mismo que una llamada al m&eacute;todo <code>sign(...)</code>
     * puesto que las multifirmas en los ficheros PDF se limitan a firmas independientes "en serie", pero no implementando los mecanismos de
     * cofirma o contrafirma de CAdES.
     * <p>
     *  Notas sobre documentos <i>certificados</i>:<br>
     *  Si un PDF firmado se ha certificado (por ejemplo, a&ntilde;adiendo una firma electr&oacute;nica usando Adobe Reader), cualquier
     *  modificaci&oacute;n posterior del fichero (como la adici&oacute;n de nuevas firmas con este m&eacute;todo) invalidar&aacute;
     *  las firmas previamente existentes.<br>
     *  Si se detecta un documento PDF certificado, se mostrar&aacute; un di&aacute;logo gr&aacute;fico advirtiendo al usuario de esta
     *  situaci&oacute;n y pidiendo confirmaci&oacute;n para continuar.<br>Si desea evitar interacciones directas con los usuarios
     *  consulte la documentaci&oacute;n de las opciones <code>allowSigningCertifiedPdfs</code> y <code>headLess</code>.<br>
     * </p>
     * <p>
     *  Notas sobre documentos protegidos con contrase&ntilde;a:<br>
     *  Si un PDF est&aacute; protegido con contrase&ntilde;a por estar cifrado, se mostrar&aacute; un di&aacute;logo gr&aacute;fico advirtiendo al usuario de esta
     *  situaci&oacute;n y solicitando la contrase&ntilde;a de apertura del PDF.<br>Si desea evitar interacciones directas con los usuarios
     *  consulte la documentaci&oacute;n de las opciones <code>ownerPassword</code> y <code>headLess</code>.
     *  Adicionalmente, es posible, si el fichero de entrada estaba cifrado y protegido con contrase&ntilde;a, que la salida sea un documento PDF
     *  igualmente cifrado y protegido con contrase&ntilde;a. Consulte la documentaci&oacute;n de la opci&oacute;n <code>avoidEncryptingSignedPdfs</code>
     *  para m&aacute;s informaci&oacute;n.
     * </p>
     * En general, es recomendable prescindir de este m&eacute;todo y llamar directamente al m&eacute;todo <code>sign(...)</code>
     * @param data Se ignora el valor de este par&aacute;metro. <b>El documento PDF debe proporcionarse mediante el par&aacute;tro <code>sign</code></b>.
     * @param sign Documento PDF a firmar
     * @param algorithm Algoritmo a usar para la firma.
     * <p>Se aceptan los siguientes algoritmos en el par&aacute;metro <code>algorithm</code>:</p>
     * <ul>
     *  <li><i>SHA1withRSA</i></li>
     *  <li><i>MD5withRSA</i> (no recomendado por vulnerable)</li>
     *  <li><i>MD2withRSA</i> (no recomendado por vulnerable)</li>
     *  <li><i>SHA256withRSA</i></li>
     *  <li><i>SHA384withRSA</i></li>
     *  <li><i>SHA512withRSA</i></li>
     * </ul>
     * @param key Clave privada a usar para firmar
     * @param certChain Cadena de certificados del firmante
     * @param extraParams Par&aacute;metros adicionales para la firma.
     * <p>Se aceptan los siguientes valores en el par&aacute;metro <code>extraParams</code>:</p>
     * <dl>
     *  <dt><b><i>certificationLevel</i></b></dt>
     *   <dd>
     *    Nivel de certificaci&oacute;n de la firma PDF.<br>Los valores admitidos son num&eacute;ricos, correspondiendo:
     *    <ul>
     *     <li><i>0</i> = Firma ordinaria no certificada (por defecto)</li>
     *     <li><i>1</i> = Firma de autor. No se permite ning&uacute;n cambio posterior en el documento</li>
     *     <li><i>2</i> = Firma de autor certificada para formularios. Se permite &uacute;nicamente el relleno posterior de los campos del formulario</li>
     *     <li><i>3</i> = Firma certificada. Se permite &uacute;nicamente el relleno posterior de los campos del formulario o el a&ntilde;adido de firmas de aprobaci&oacute;n</li>
     *    </ul>
     *   </dd>
     *  <dt><b><i>layer2Text</i></b></dt>
     *   <dd>
     *    Texto a escribir dentro de la "capa 2" de la firma visible. Este texto se escribe &uacute;nicamente si no se ha especificado una
     *    imagen de r&uacute;brica.
     *   </dd>
     *  <dt><b><i>layer2FontFamily</i></b></dt>
     *   <dd>
     *    Tipo de letra a usar en el texto de la "capa 2" de la firma visible. Este par&aacute;metro requiere que se haya establecido tambi&eacute;n
     *    el par&aacute;metro <code>layer2Text</code>.<br>Los valores admitidos son num&eacute;ricos, correspondiendo:
     *    <ul>
     *     <li><i>0</i> = Courier (tipo por defecto)</li>
     *     <li><i>1</i> = Helvetica</li>
     *     <li><i>2</i> = Times Roman</li>
     *     <li><i>3</i> = Symbol</li>
     *     <li><i>4</i> = ZapfDingBats</li>
     *    </ul>
     *   </dd>
     *  <dt><b><i>layer2FontSize</i></b></dt>
     *   <dd>
     *    Tama&ntilde;o de de letra a usar en el texto de la "capa 2" de la firma visible. Este par&aacute;metro requiere que se haya establecido
     *    tambi&eacute;n el par&aacute;metro <code>layer2Text</code>.<br>Los valores admitidos son num&eacute;ricos (y el valor por defecto es 12).
     *   </dd>
     *  <dt><b><i>layer2FontStyle</i></b></dt>
     *   <dd>
     *    Estilo del tipo de letra a usar en el texto de la "capa 2" de la firma visible. Este par&aacute;metro requiere que se haya establecido
     *    tambi&eacute;n el par&aacute;metro <code>layer2Text</code>.<br>Los valores admitidos son num&eacute;ricos, correspondiendo:
     *    <ul>
     *     <li><i>0</i> = Normal (estilo por defecto)</li>
     *     <li><i>1</i> = Negrita</li>
     *     <li><i>2</i> = Cursiva</li>
     *     <li><i>3</i> = Negrita y cursiva</li>
     *     <li><i>4</i> = Subrayado</li>
     *     <li><i>4</i> = Tachado</li>
     *    </ul>
     *    Es posible combinar estilos aplicando la operaci&oacute;n l&oacute;gica <cite>o</cite> sobre los valores num&eacute;ricos a combinar.
     *   </dd>
     *  <dt><b><i>layer2FontColor</i></b></dt>
     *   <dd>
     *    Color del texto de la "capa 2" de la firma visible. Este par&aacute;metro requiere que se haya establecido
     *    tambi&eacute;n el par&aacute;metro <code>layer2Text</code>.<br>Los valores admitidos son textuales (se ignora entre may&uacute;sculas
     *    y min&uacute;sculas), soport&aacute;ndose:
     *    <ul>
     *     <li><i>black</i> = Negro (color por defecto)</li>
     *     <li><i>white</i> = Blanco</li>
     *     <li><i>gray</i> = Gris</li>
     *     <li><i>lightGray</i> = Gris claro</li>
     *     <li><i>darkGray</i> = Gris oscuro</li>
     *     <li><i>red</i> = Rojo</li>
     *     <li><i>pink</i> = Rosa</li>
     *    </ul>
     *   </dd>
     *  <dt><b><i>layer4Text</i></b></dt>
     *   <dd>
     *    Texto a escribir dentro de la "capa 4" de la firma visible. Este texto se escribe &uacute;nicamente si no se ha especificado una
     *    imagen de r&uacute;brica.
     *   </dd>
     *  <dt><b><i>signatureSubFilter</i></b></dt>
     *   <dd>
     *    Nombre del sub-filtro en el diccionario PDF para indicar el tipo de la firma. Si no se indica este par&aacute;metro por defecto se
     *    usa <code>adbe.pkcs7.detached</code> (firma PAdES b&aacute;sica).
     *    Es posible indicar <code>ETSI.CAdES.detached</code> para generar una firma PAdES-BES, si bien el hacerlo puede causar que al a&ntilde;adir firmas adicionales
     *    al PDF se invaliden las ya existentes.
     *   </dd>
     *  <dt><b><i>signatureField</i></b></dt>
     *   <dd>
     *    Nombre del campo en donde insertar la firma.
     *    Si el documento PDF tiene ya un campo de firma precreado es posible utilizarlo para insertar la firma generada, referenci&aacute;ndolo
     *    por su nombre.<br>
     *    Si se indica un nombre de campo de firma que no exista en el documento PDF proporcionado, se generar&aacute; una excepci&oacute;n.
     *   </dd>
     *  <dt><b><i>signatureRubricImage</i></b></dt>
     *   <dd>Imagen JPEG codificada en Base64 de la r&uacute;brica de la firma manuscrita que se desea aparezca como firma visible en el PDF.</dd>
     *  <dt><b><i>signaturePage</i></b></dt>
     *   <dd>
     *    P&aacute;gina del documento PDF donde insertar la firma. Puede usarse la constante <code>LAST_PAGE</code>
     *    para referirse a la &uacute;ltima p&aacute;gina del documento PDF si se desconoce el n&uacute;mero total de
     *    p&aacute;ginas de este.<br>
     *    Este par&aacute;metro se ignora si se ha establecido valor al par&aacute;metro <i>signatureField</i> y necesita que se
     *    establezcan valores v&aacute;lidos a los par&aacute;metros <i>signaturePositionOnPageLowerLeftX</i>, <i>signaturePositionOnPageLowerLeftY</i>,
     *    <i>signaturePositionOnPageUpperRightX</i> y <i>signaturePositionOnPageUpperRightY</i>.
     *   </dd>
     *  <dt><b><i>signaturePositionOnPageLowerLeftX</i></b></dt>
     *   <dd>
     *    Coordenada horizontal inferior izquiera de la posici&oacute;n del recuadro visible de la fimra dentro de la p&aacute;gina.<br>
     *    Es necesario indicar el resto de coordenadas del recuadro mediante los par&aacute;metros <i>signaturePositionOnPageLowerLeftY</i>,
     *    <i>signaturePositionOnPageUpperRightX</i> y <i>signaturePositionOnPageUpperRightY</i>.<br>
     *    Si no se indica una p&aacute;gina en el par&aacute;metro <i>signaturePage</i> la firma se inserta en la &uacute;ltima p&aacute;gina
     *    del documento.
     *   </dd>
     *  <dt><b><i>signaturePositionOnPageLowerLeftY</i></b></dt>
     *   <dd>
     *    Coordenada vertical inferior izquiera de la posici&oacute;n del recuadro visible de la fimra dentro de la p&aacute;gina.<br>
     *    Es necesario indicar el resto de coordenadas del recuadro mediante los par&aacute;metros <i>signaturePositionOnPageLowerLeftX</i>,
     *    <i>signaturePositionOnPageUpperRightX</i> y <i>signaturePositionOnPageUpperRightY</i>.<br>
     *    Si no se indica una p&aacute;gina en el par&aacute;metro <i>signaturePage</i> la firma se inserta en la &uacute;ltima p&aacute;gina
     *    del documento.
     *   </dd>
     *  <dt><b><i>signaturePositionOnPageUpperRightX</i></b></dt>
     *   <dd>
     *    Coordenada horizontal superior derecha de la posici&oacute;n del recuadro visible de la fimra dentro de la p&aacute;gina.<br>
     *    Es necesario indicar el resto de coordenadas del recuadro mediante los par&aacute;metros <i>signaturePositionOnPageLowerLeftX</i>,
     *    <i>signaturePositionOnPageLowerLeftY</i> y <i>signaturePositionOnPageUpperRightY</i>.<br>
     *    Si no se indica una p&aacute;gina en el par&aacute;metro <i>signaturePage</i> la firma se inserta en la &uacute;ltima p&aacute;gina
     *    del documento.
     *   </dd>
     *  <dt><b><i>signaturePositionOnPageUpperRightY</i></b></dt>
     *   <dd>
     *    Coordenada vertical superior derecha de la posici&oacute;n del recuadro visible de la fimra dentro de la p&aacute;gina.<br>
     *    Es necesario indicar el resto de coordenadas del recuadro mediante los par&aacute;metros <i>signaturePositionOnPageLowerLeftX</i>,
     *    <i>signaturePositionOnPageLowerLeftY</i> y <i>signaturePositionOnPageUpperRightX</i>.<br>
     *    Si no se indica una p&aacute;gina en el par&aacute;metro <i>signaturePage</i> la firma se inserta en la &uacute;ltima p&aacute;gina
     *    del documento.
     *   </dd>
     *  <dt><b><i>applySystemDate</i></b></dt>
     *   <dd><code>true</code> si se desea usar la hora y fecha del sistema como hora y fecha de firma, <code>false</code> en caso contrario.
     *  <dt><b><i>signReason</i></b></dt>
     *   <dd>Raz&oacute;n por la que se realiza la firma (este dato se a&ntilde;ade al diccionario PDF, y no a la propia firma).</dd>
     *  <dt><b><i>signatureProductionCity</i></b></dt>
     *   <dd>Ciudad en la que se realiza la firma (este dato se a&ntilde;ade al diccionario PDF, y no a la propia firma).</dd>
     *  <dt><b><i>signerContact</i></b></dt>
     *   <dd>
     *    Contacto del firmante, usualmente una direcci&oacute;n de coreo electr&oacute;nico
     *    (este dato se a&ntilde;ade al diccionario PDF, y no a la propia firma).
     *   </dd>
     *  <dt><b><i>policyIdentifier</i></b></dt>
     *   <dd>
     *    Identificador de la pol&iacute;tica de firma. Debe ser un OID (o una URN de tipo OID) que identifique
     *    &uacute;nivocamente la pol&iacute;tica en formato ASN.1 procesable.
     *   </dd>
     *  <dt><b><i>policyIdentifierHash</i></b></dt>
     *   <dd>
     *    Huella digital del documento de pol&iacute;tica de firma (normalmente del mismo fichero en formato ASN.1 procesable).
     *    Si no se indica una huella digital y el par&aacute;metro <code>policyIdentifier</code> no es una URL accesible
     *    universalmente se usar&aacute; <code>0</code>, mientras que si no se indica una huella digital pero el par&aacute;metro
     *    <code>policyIdentifier</code> es una URL accesible universalmente, se descargara el fichero apuntado por la URL para calcular la huella
     *    digital <i>al vuelo</i>.
     *   </dd>
     *  <dt><b><i>policyIdentifierHashAlgorithm</i></b></dt>
     *   <dd>
     *    Algoritmo usado para el c&aacute;lculo de la huella digital indicada en el par&aacute;metro <code>policyIdentifierHash</code>.
     *    Es obligario indicarlo cuando se proporciona una huella digital distinta de <code>0</code>.
     *   </dd>
     *  <dt><b><i>policyQualifier</i></b></dt>
     *   <dd>
     *    URL que apunta al documento descriptivo de la pol&iacute;tica de firma (normalmente un documento PDF con una descripci&oacute;n textual).
     *   </dd>
     *  <dt><b><i>ownerPassword</i></b></dt>
     *   <dd>
     *    Contrase&ntilde;a de apertura del PDF (contrase&ntilde;a del propietario) si este estaba cifrado.<br>
     *    No se soporta la firma de documentos PDF cifrados con certificados o con algoritmo AES256.
     *   </dd>
     *  <dt><b><i>headLess</i></b></dt>
     *   <dd>
     *    Evita cualquier interacci&oacute;n con el usuario si se establece a <code>true</code>, si no se establece o se establece a <code>false</code>
     *    act&uacute;a normalmente (puede mostrar di&aacute;logos, por ejemplo, para solicitar las contrase&ntilde;as de los PDF cifrados). &Uacute;til para
     *    los procesos desatendidos y por lotes
     *   </dd>
     *  <dt><b><i>avoidEncryptingSignedPdfs</i></b></dt>
     *   <dd>
     *    Si se establece a <code>true</code> no cifra los PDF firmados aunque el original estuviese firmado, si no se establece o se establece a
     *    <code>false</code> los PDF se cifran tras firmarse si el original lo estaba, usando la misma contrase&ntilde;a y opciones que este
     *   </dd>
     *  <dt><b><i>allowSigningCertifiedPdfs</i></b></dt>
     *   <dd>
     *    Si se establece a <code>true</code> permite la firma o cofirma de PDF certificados sin consultarlo al usuario, si se establece a
     *    <code>false</code> o cualquier otro valor se lanza una excepci&oacute;n en caso de intentar firmar o cofirmar un PDF certificado y
     *    si no se establece se mostrar&aacute; un di&aacute;logo al usuario para que confirme que desea realizar la firma a pesar de que
     *    el resultado ser&aacute;n una firma no v&aacute;lida.<br>
     *    <b>Si el par&aacute;metro <code>headLess</code> est&aacute; establecido a <code>true</code>, no podr&aacute; mostrar el di&aacute;logo
     *    de confirmaci&oacute;n as&iacute; que llegados a este punto se lanzar&aacute; una excepci&oacute;n.</b><br>
     *    No se soporta el cifrado de documentos PDF con certificados o con algoritmo AES256.
     *   </dd>
     *  <dt><b><i>tsaURL</i></b></dt>
     *   <dd>URL de la autoridad de sello de tiempo (si no se indica no se a&ntilde;ade sello de tiempo).</dd>
     *  <dt><b><i>tsaPolicy</i></b></dt>
     *   <dd>Pol&iacute;tica de sellado de tiempo (obligatoria si se indica <code>tsaURL</code>).</dd>
     *  <dt><b><i>tsaHashAlgorithm</i></b></dt>
     *   <dd>Algoritmo de huella digital a usar para el sello de tiempo (si no se establece se usa SHA-1).</dd>
     *  <dt><b><i>tsaRequireCert</i></b></dt>
     *   <dd><code>true</code> si se requiere el certificado de la TSA, false en caso contrario (si no se establece se asume <code>true</code>).</dd>
     *  <dt><b><i>tsaUsr</i></b></dt>
     *   <dd>Nombre de usuario de la TSA.</dd>
     *  <dt><b><i>tsaPwd</i></b></dt>
     *   <dd>Contrase&ntilde;a del usuario de la TSA. Se ignora si no de ha establecido adem&aacute;s <code>tsaUsr</code>.</dd>
     *  <dt><b><i>signingCertificateV2</i></b></dt>
     *   <dd>Si se indica a {@code true} se utilizar SigningCertificateV2, si se indica cualquier otra cosa SigningCertificateV1.
     *   Si no se indica nada, se utilizar&aacute; V1 para las firmas SHA1 y V2 para el resto.</dd>
     * </dl>
     * @return Documento PDF firmado en formato PAdES
     * @throws AOException Cuando ocurre cualquier problema durante el proceso
     * @throws IOException */
    @Override
	public byte[] cosign(final byte[] data,
                         final byte[] sign,
                         final String algorithm,
                         final PrivateKey key,
                         final java.security.cert.Certificate[] certChain,
                         final Properties extraParams) throws AOException, IOException {
        return sign(sign, algorithm, key, certChain, extraParams);
    }

    /** A&ntilde;ade una firma PAdES a un documento PDF. El comportamiento es exactamente el mismo que una llamada al m&eacute;todo <code>sign(...)</code>
     * puesto que las multifirmas en los ficheros PDF se limitan a firmas independientes "en serie", pero no implementando los mecanismos de
     * cofirma o contrafirma de CAdES.
     * <p>
     *  Notas sobre documentos <i>certificados</i>:<br>
     *  Si un PDF firmado se ha certificado (por ejemplo, a&ntilde;adiendo una firma electr&oacute;nica usando Adobe Reader), cualquier
     *  modificaci&oacute;n posterior del fichero (como la adici&oacute;n de nuevas firmas con este m&eacute;todo) invalidar&aacute;
     *  las firmas previamente existentes.<br>
     *  Si se detecta un documento PDF certificado, se mostrar&aacute; un di&aacute;logo gr&aacute;fico advirtiendo al usuario de esta
     *  situaci&oacute;n y pidiendo confirmaci&oacute;n para continuar.<br>Si desea evitar interacciones directas con los usuarios
     *  consulte la documentaci&oacute;n de las opciones <code>allowSigningCertifiedPdfs</code> y <code>headLess</code>.<br>
     * </p>
     * <p>
     *  Notas sobre documentos protegidos con contrase&ntilde;a:<br>
     *  Si un PDF est&aacute; protegido con contrase&ntilde;a por estar cifrado, se mostrar&aacute; un di&aacute;logo gr&aacute;fico advirtiendo al usuario de esta
     *  situaci&oacute;n y solicitando la contrase&ntilde;a de apertura del PDF.<br>Si desea evitar interacciones directas con los usuarios
     *  consulte la documentaci&oacute;n de las opciones <code>ownerPassword</code> y <code>headLess</code>.
     *  Adicionalmente, es posible, si el fichero de entrada estaba cifrado y protegido con contrase&ntilde;a, que la salida sea un documento PDF
     *  igualmente cifrado y protegido con contrase&ntilde;a. Consulte la documentaci&oacute;n de la opci&oacute;n <code>avoidEncryptingSignedPdfs</code>
     *  para m&aacute;s informaci&oacute;n.
     * </p>
     * En general, es recomendable prescindir de este m&eacute;todo y llamar directamente al m&eacute;todo <code>sign(...)</code>
     * @param sign Documento PDF a firmar
     * @param algorithm Algoritmo a usar para la firma.
     * <p>Se aceptan los siguientes algoritmos en el par&aacute;metro <code>algorithm</code>:</p>
     * <ul>
     *  <li><i>SHA1withRSA</i></li>
     *  <li><i>MD5withRSA</i> (no recomendado por vulnerable)</li>
     *  <li><i>MD2withRSA</i> (no recomendado por vulnerable)</li>
     *  <li><i>SHA256withRSA</i></li>
     *  <li><i>SHA384withRSA</i></li>
     *  <li><i>SHA512withRSA</i></li>
     * </ul>
     * @param key Clave privada a usar para firmar
     * @param certChain Cadena de certificados del firmante
     * @param extraParams Par&aacute;metros adicionales para la firma.
     * <p>Se aceptan los siguientes valores en el par&aacute;metro <code>extraParams</code>:</p>
     * <dl>
     *  <dt><b><i>certificationLevel</i></b></dt>
     *   <dd>
     *    Nivel de certificaci&oacute;n de la firma PDF.<br>Los valores admitidos son num&eacute;ricos, correspondiendo:
     *    <ul>
     *     <li><i>0</i> = Firma ordinaria no certificada (por defecto)</li>
     *     <li><i>1</i> = Firma de autor. No se permite ning&uacute;n cambio posterior en el documento</li>
     *     <li><i>2</i> = Firma de autor certificada para formularios. Se permite &uacute;nicamente el relleno posterior de los campos del formulario</li>
     *     <li><i>3</i> = Firma certificada. Se permite &uacute;nicamente el relleno posterior de los campos del formulario o el a&ntilde;adido de firmas de aprobaci&oacute;n</li>
     *    </ul>
     *   </dd>
     *  <dt><b><i>layer2Text</i></b></dt>
     *   <dd>
     *    Texto a escribir dentro de la "capa 2" de la firma visible. Este texto se escribe &uacute;nicamente si no se ha especificado una
     *    imagen de r&uacute;brica.
     *   </dd>
     *  <dt><b><i>layer2FontFamily</i></b></dt>
     *   <dd>
     *    Tipo de letra a usar en el texto de la "capa 2" de la firma visible. Este par&aacute;metro requiere que se haya establecido tambi&eacute;n
     *    el par&aacute;metro <code>layer2Text</code>.<br>Los valores admitidos son num&eacute;ricos, correspondiendo:
     *    <ul>
     *     <li><i>0</i> = Courier (tipo por defecto)</li>
     *     <li><i>1</i> = Helvetica</li>
     *     <li><i>2</i> = Times Roman</li>
     *     <li><i>3</i> = Symbol</li>
     *     <li><i>4</i> = ZapfDingBats</li>
     *    </ul>
     *   </dd>
     *  <dt><b><i>layer2FontSize</i></b></dt>
     *   <dd>
     *    Tama&ntilde;o de de letra a usar en el texto de la "capa 2" de la firma visible. Este par&aacute;metro requiere que se haya establecido
     *    tambi&eacute;n el par&aacute;metro <code>layer2Text</code>.<br>Los valores admitidos son num&eacute;ricos (y el valor por defecto es 12).
     *   </dd>
     *  <dt><b><i>layer2FontStyle</i></b></dt>
     *   <dd>
     *    Estilo del tipo de letra a usar en el texto de la "capa 2" de la firma visible. Este par&aacute;metro requiere que se haya establecido
     *    tambi&eacute;n el par&aacute;metro <code>layer2Text</code>.<br>Los valores admitidos son num&eacute;ricos, correspondiendo:
     *    <ul>
     *     <li><i>0</i> = Normal (estilo por defecto)</li>
     *     <li><i>1</i> = Negrita</li>
     *     <li><i>2</i> = Cursiva</li>
     *     <li><i>3</i> = Negrita y cursiva</li>
     *     <li><i>4</i> = Subrayado</li>
     *     <li><i>4</i> = Tachado</li>
     *    </ul>
     *    Es posible combinar estilos aplicando la operaci&oacute;n l&oacute;gica <cite>o</cite> sobre los valores num&eacute;ricos a combinar.
     *   </dd>
     *  <dt><b><i>layer2FontColor</i></b></dt>
     *   <dd>
     *    Color del texto de la "capa 2" de la firma visible. Este par&aacute;metro requiere que se haya establecido
     *    tambi&eacute;n el par&aacute;metro <code>layer2Text</code>.<br>Los valores admitidos son textuales (se ignora entre may&uacute;sculas
     *    y min&uacute;sculas), soport&aacute;ndose:
     *    <ul>
     *     <li><i>black</i> = Negro (color por defecto)</li>
     *     <li><i>white</i> = Blanco</li>
     *     <li><i>gray</i> = Gris</li>
     *     <li><i>lightGray</i> = Gris claro</li>
     *     <li><i>darkGray</i> = Gris oscuro</li>
     *     <li><i>red</i> = Rojo</li>
     *     <li><i>pink</i> = Rosa</li>
     *    </ul>
     *   </dd>
     *  <dt><b><i>layer4Text</i></b></dt>
     *   <dd>
     *    Texto a escribir dentro de la "capa 4" de la firma visible. Este texto se escribe &uacute;nicamente si no se ha especificado una
     *    imagen de r&uacute;brica.
     *   </dd>
     *  <dt><b><i>signatureSubFilter</i></b></dt>
     *   <dd>
     *    Nombre del sub-filtro en el diccionario PDF para indicar el tipo de la firma. Si no se indica este par&aacute;metro por defecto se
     *    usa <code>adbe.pkcs7.detached</code> (firma PAdES b&aacute;sica).
     *    Es posible indicar <code>ETSI.CAdES.detached</code> para generar una firma PAdES-BES, si bien el hacerlo puede causar que al a&ntilde;adir firmas adicionales
     *    al PDF se invaliden las ya existentes.
     *   </dd>
     *  <dt><b><i>signatureField</i></b></dt>
     *   <dd>
     *    Nombre del campo en donde insertar la firma.
     *    Si el documento PDF tiene ya un campo de firma precreado es posible utilizarlo para insertar la firma generada, referenci&aacute;ndolo
     *    por su nombre.<br>
     *    Si se indica un nombre de campo de firma que no exista en el documento PDF proporcionado, se generar&aacute; una excepci&oacute;n.
     *   </dd>
     *  <dt><b><i>signatureRubricImage</i></b></dt>
     *   <dd>Imagen JPEG codificada en Base64 de la r&uacute;brica de la firma manuscrita que se desea aparezca como firma visible en el PDF.</dd>
     *  <dt><b><i>signaturePage</i></b></dt>
     *   <dd>
     *    P&aacute;gina del documento PDF donde insertar la firma. Puede usarse la constante <code>LAST_PAGE</code>
     *    para referirse a la &uacute;ltima p&aacute;gina del documento PDF si se desconoce el n&uacute;mero total de
     *    p&aacute;ginas de este.<br>
     *    Este par&aacute;metro se ignora si se ha establecido valor al par&aacute;metro <i>signatureField</i> y necesita que se
     *    establezcan valores v&aacute;lidos a los par&aacute;metros <i>signaturePositionOnPageLowerLeftX</i>, <i>signaturePositionOnPageLowerLeftY</i>,
     *    <i>signaturePositionOnPageUpperRightX</i> y <i>signaturePositionOnPageUpperRightY</i>.
     *   </dd>
     *  <dt><b><i>signaturePositionOnPageLowerLeftX</i></b></dt>
     *   <dd>
     *    Coordenada horizontal inferior izquiera de la posici&oacute;n del recuadro visible de la fimra dentro de la p&aacute;gina.<br>
     *    Es necesario indicar el resto de coordenadas del recuadro mediante los par&aacute;metros <i>signaturePositionOnPageLowerLeftY</i>,
     *    <i>signaturePositionOnPageUpperRightX</i> y <i>signaturePositionOnPageUpperRightY</i>.<br>
     *    Si no se indica una p&aacute;gina en el par&aacute;metro <i>signaturePage</i> la firma se inserta en la &uacute;ltima p&aacute;gina
     *    del documento.
     *   </dd>
     *  <dt><b><i>signaturePositionOnPageLowerLeftY</i></b></dt>
     *   <dd>
     *    Coordenada vertical inferior izquiera de la posici&oacute;n del recuadro visible de la fimra dentro de la p&aacute;gina.<br>
     *    Es necesario indicar el resto de coordenadas del recuadro mediante los par&aacute;metros <i>signaturePositionOnPageLowerLeftX</i>,
     *    <i>signaturePositionOnPageUpperRightX</i> y <i>signaturePositionOnPageUpperRightY</i>.<br>
     *    Si no se indica una p&aacute;gina en el par&aacute;metro <i>signaturePage</i> la firma se inserta en la &uacute;ltima p&aacute;gina
     *    del documento.
     *   </dd>
     *  <dt><b><i>signaturePositionOnPageUpperRightX</i></b></dt>
     *   <dd>
     *    Coordenada horizontal superior derecha de la posici&oacute;n del recuadro visible de la fimra dentro de la p&aacute;gina.<br>
     *    Es necesario indicar el resto de coordenadas del recuadro mediante los par&aacute;metros <i>signaturePositionOnPageLowerLeftX</i>,
     *    <i>signaturePositionOnPageLowerLeftY</i> y <i>signaturePositionOnPageUpperRightY</i>.<br>
     *    Si no se indica una p&aacute;gina en el par&aacute;metro <i>signaturePage</i> la firma se inserta en la &uacute;ltima p&aacute;gina
     *    del documento.
     *   </dd>
     *  <dt><b><i>signaturePositionOnPageUpperRightY</i></b></dt>
     *   <dd>
     *    Coordenada vertical superior derecha de la posici&oacute;n del recuadro visible de la fimra dentro de la p&aacute;gina.<br>
     *    Es necesario indicar el resto de coordenadas del recuadro mediante los par&aacute;metros <i>signaturePositionOnPageLowerLeftX</i>,
     *    <i>signaturePositionOnPageLowerLeftY</i> y <i>signaturePositionOnPageUpperRightX</i>.<br>
     *    Si no se indica una p&aacute;gina en el par&aacute;metro <i>signaturePage</i> la firma se inserta en la &uacute;ltima p&aacute;gina
     *    del documento.
     *   </dd>
     *  <dt><b><i>applySystemDate</i></b></dt>
     *   <dd><code>true</code> si se desea usar la hora y fecha del sistema como hora y fecha de firma, <code>false</code> en caso contrario.
     *  <dt><b><i>signReason</i></b></dt>
     *   <dd>Raz&oacute;n por la que se realiza la firma (este dato se a&ntilde;ade al diccionario PDF, y no a la propia firma).</dd>
     *  <dt><b><i>signatureProductionCity</i></b></dt>
     *   <dd>Ciudad en la que se realiza la firma (este dato se a&ntilde;ade al diccionario PDF, y no a la propia firma).</dd>
     *  <dt><b><i>signerContact</i></b></dt>
     *   <dd>
     *    Contacto del firmante, usualmente una direcci&oacute;n de coreo electr&oacute;nico
     *    (este dato se a&ntilde;ade al diccionario PDF, y no a la propia firma).
     *   </dd>
     *  <dt><b><i>policyIdentifier</i></b></dt>
     *   <dd>
     *    Identificador de la pol&iacute;tica de firma. Debe ser un OID (o una URN de tipo OID) que identifique
     *    &uacute;nivocamente la pol&iacute;tica en formato ASN.1 procesable.
     *   </dd>
     *  <dt><b><i>policyIdentifierHash</i></b></dt>
     *   <dd>
     *    Huella digital del documento de pol&iacute;tica de firma (normalmente del mismo fichero en formato ASN.1 procesable).
     *    Si no se indica una huella digital y el par&aacute;metro <code>policyIdentifier</code> no es una URL accesible
     *    universalmente se usar&aacute; <code>0</code>, mientras que si no se indica una huella digital pero el par&aacute;metro
     *    <code>policyIdentifier</code> es una URL accesible universalmente, se descargara el fichero apuntado por la URL para calcular la huella
     *    digital <i>al vuelo</i>.
     *   </dd>
     *  <dt><b><i>policyIdentifierHashAlgorithm</i></b></dt>
     *   <dd>
     *    Algoritmo usado para el c&aacute;lculo de la huella digital indicada en el par&aacute;metro <code>policyIdentifierHash</code>.
     *    Es obligario indicarlo cuando se proporciona una huella digital distinta de <code>0</code>.
     *   </dd>
     *  <dt><b><i>policyQualifier</i></b></dt>
     *   <dd>
     *    URL que apunta al documento descriptivo de la pol&iacute;tica de firma (normalmente un documento PDF con una descripci&oacute;n textual).
     *   </dd>
     *  <dt><b><i>ownerPassword</i></b></dt>
     *   <dd>
     *    Contrase&ntilde;a de apertura del PDF (contrase&ntilde;a del propietario) si este estaba cifrado.<br>
     *    No se soporta la firma de documentos PDF cifrados con certificados o con algoritmo AES256.
     *   </dd>
     *  <dt><b><i>headLess</i></b></dt>
     *   <dd>
     *    Evita cualquier interacci&oacute;n con el usuario si se establece a <code>true</code>, si no se establece o se establece a <code>false</code>
     *    act&uacute;a normalmente (puede mostrar di&aacute;logos, por ejemplo, para solicitar las contrase&ntilde;as de los PDF cifrados). &Uacute;til para
     *    los procesos desatendidos y por lotes
     *   </dd>
     *  <dt><b><i>avoidEncryptingSignedPdfs</i></b></dt>
     *   <dd>
     *    Si se establece a <code>true</code> no cifra los PDF firmados aunque el original estuviese firmado, si no se establece o se establece a
     *    <code>false</code> los PDF se cifran tras firmarse si el original lo estaba, usando la misma contrase&ntilde;a y opciones que este
     *   </dd>
     *  <dt><b><i>allowSigningCertifiedPdfs</i></b></dt>
     *   <dd>
     *    Si se establece a <code>true</code> permite la firma o cofirma de PDF certificados sin consultarlo al usuario, si se establece a
     *    <code>false</code> o cualquier otro valor se lanza una excepci&oacute;n en caso de intentar firmar o cofirmar un PDF certificado y
     *    si no se establece se mostrar&aacute; un di&aacute;logo al usuario para que confirme que desea realizar la firma a pesar de que
     *    el resultado ser&aacute;n una firma no v&aacute;lida.<br>
     *    <b>Si el par&aacute;metro <code>headLess</code> est&aacute; establecido a <code>true</code>, no podr&aacute; mostrar el di&aacute;logo
     *    de confirmaci&oacute;n as&iacute; que llegados a este punto se lanzar&aacute; una excepci&oacute;n.</b><br>
     *    No se soporta el cifrado de documentos PDF con certificados o con algoritmo AES256.
     *   </dd>
     *  <dt><b><i>tsaURL</i></b></dt>
     *   <dd>URL de la autoridad de sello de tiempo (si no se indica no se a&ntilde;ade sello de tiempo).</dd>
     *  <dt><b><i>tsaPolicy</i></b></dt>
     *   <dd>Pol&iacute;tica de sellado de tiempo (obligatoria si se indica <code>tsaURL</code>).</dd>
     *  <dt><b><i>tsaHashAlgorithm</i></b></dt>
     *   <dd>Algoritmo de huella digital a usar para el sello de tiempo (si no se establece se usa SHA-1).</dd>
     *  <dt><b><i>tsaRequireCert</i></b></dt>
     *   <dd><code>true</code> si se requiere el certificado de la TSA, false en caso contrario (si no se establece se asume <code>true</code>).</dd>
     *  <dt><b><i>tsaUsr</i></b></dt>
     *   <dd>Nombre de usuario de la TSA.</dd>
     *  <dt><b><i>tsaPwd</i></b></dt>
     *   <dd>Contrase&ntilde;a del usuario de la TSA. Se ignora si no de ha establecido adem&aacute;s <code>tsaUsr</code>.</dd>
     *  <dt><b><i>signingCertificateV2</i></b></dt>
     *   <dd>Si se indica a {@code true} se utilizar SigningCertificateV2, si se indica cualquier otra cosa SigningCertificateV1.
     *   Si no se indica nada, se utilizar&aacute; V1 para las firmas SHA1 y V2 para el resto.</dd>
     * </dl>
     * @return Documento PDF firmado en formato PAdES
     * @throws AOException Cuando ocurre cualquier problema durante el proceso
     * @throws IOException */
    @Override
	public byte[] cosign(final byte[] sign,
			             final String algorithm,
			             final PrivateKey key,
			             final java.security.cert.Certificate[] certChain,
			             final Properties extraParams) throws AOException, IOException {
        return sign(sign, algorithm, key, certChain, extraParams);
    }

    /** Operaci&oacute;n no soportada para firmas PAdES. */
    @Override
	public byte[] countersign(final byte[] sign,
                              final String algorithm,
                              final CounterSignTarget targetType,
                              final Object[] targets,
                              final PrivateKey key,
                              final java.security.cert.Certificate[] certChain,
                              final Properties extraParams) throws AOException {
        throw new UnsupportedOperationException("No es posible realizar contrafirmas de ficheros PDF"); //$NON-NLS-1$
    }

    /** Devuelve el nombre de fichero de firma predeterminado que se recomienda usar para
     * un PDF firmado con nombre original igual al proporcionado.
     * En este caso el resultado ser&aacute; siempre el nombre original m&aacute;s un
     * sufijo adicional (opcional) previo a la extensi&oacute;n.
     * Siempre se termina el nombre de fichero con la extensi&oacute;n <i>.pdf</i>, incluso si el nombre original carec&iacute;a de esta.
     * @param originalName Nombre del fichero original que se firma.
     * @param inText Sufijo a agregar al nombre de fichero devuelto, inmediatamente anterior a la extensi&oacute;n.
     * @return Nombre apropiado para el fichero de firma. */
    @Override
	public String getSignedName(final String originalName, final String inText) {
        final String inTextInt = inText != null ? inText : ""; //$NON-NLS-1$
        if (originalName == null) {
            return "signed.pdf"; //$NON-NLS-1$
        }
        if (originalName.toLowerCase(Locale.US).endsWith(PDF_FILE_SUFFIX)) {
            return originalName.substring(0, originalName.length() - PDF_FILE_SUFFIX.length()) + inTextInt + PDF_FILE_SUFFIX;
        }
        return originalName + inTextInt + PDF_FILE_SUFFIX;
    }

    /** Recupera el &aacute;rbol de nodos de firma de una firma electr&oacute;nica.
     * Los nodos del &aacute;rbol ser&aacute;n textos con el <i>CommonName</i> (CN X.500)
     * del titular del certificado u objetos de tipo AOSimpleSignInfo con la
     * informaci&oacute;n b&aacute;sica de las firmas individuales, seg&uacute;n
     * el valor del par&aacute;metro <code>asSimpleSignInfo</code>. Los nodos se
     * mostrar&aacute;n en el mismo orden y con la misma estructura con el que
     * aparecen en la firma electr&oacute;nica.<br>
     * La propia estructura de firma se considera el nodo ra&iacute;z, la firma y cofirmas
     * pender&aacute;n directamentede de este.
     * @param sign Firma electr&oacute;nica de la que se desea obtener la estructura.
     * @param asSimpleSignInfo
     *        Si es <code>true</code> se devuelve un &aacute;rbol con la
     *        informaci&oacute;n b&aacute;sica de cada firma individual
     *        mediante objetos <code>AOSimpleSignInfo</code>, si es <code>false</code> un &aacute;rbol con los nombres (CN X.500) de los
     *        titulares certificados.
     * @return &Aacute;rbol de nodos de firma o <code>null</code> en caso de error. */
    @Override
	public AOTreeModel getSignersStructure(final byte[] sign, final boolean asSimpleSignInfo) {

    	isPdfFile(sign);

        final AOTreeNode root = new AOTreeNode("Datos"); //$NON-NLS-1$
        final AcroFields af;

        PdfReader pdfReader;
        try {
            pdfReader = new PdfReader(sign);
        }
        catch (final BadPasswordException e) {
            try {
                pdfReader = new PdfReader(
            		sign,
            		new String(
        				AOUIFactory.getPassword(
    						PDFMessages.getString("AOPDFSigner.0"), //$NON-NLS-1$
    						null
						)
					).getBytes()
        		);
            }
            catch (final BadPasswordException e2) {
                LOGGER.severe("La contrasena del PDF no es valida, se devolvera un arbol vacio: " + e2); //$NON-NLS-1$
                return new AOTreeModel(root, root.getChildCount());
            }
            catch (final Exception e3) {
                LOGGER.severe("No se ha podido leer el PDF, se devolvera un arbol vacio: " + e3); //$NON-NLS-1$
                return new AOTreeModel(root, root.getChildCount());
            }
        }
        catch (final Exception e) {
            LOGGER.severe("No se ha podido leer el PDF, se devolvera un arbol vacio: " + e); //$NON-NLS-1$
            return new AOTreeModel(root, root.getChildCount());
        }

        try {
            af = pdfReader.getAcroFields();
        }
        catch (final Exception e) {
            LOGGER.severe("No se ha podido obtener la informacion de los firmantes del PDF, se devolvera un arbol vacio: " + e); //$NON-NLS-1$
            return new AOTreeModel(root, root.getChildCount());
        }
        final ArrayList<?> names = af.getSignatureNames();
        Object pkcs1Object = null;
        for (int i = 0; i < names.size(); ++i) {
            final PdfPKCS7 pcks7 = af.verifySignature(names.get(i).toString());
            if (asSimpleSignInfo) {
                final AOSimpleSignInfo ssi = new AOSimpleSignInfo(new X509Certificate[] {
                    pcks7.getSigningCertificate()
                }, pcks7.getSignDate().getTime());

                // Extraemos el PKCS1 de la firma
                try {
                    // iText antiguo
                    final Field digestField = Class.forName("com.lowagie.text.pdf.PdfPKCS7").getDeclaredField("digest"); //$NON-NLS-1$ //$NON-NLS-2$
                    // En iText nuevo seria "final Field digestField = Class.forName("com.itextpdf.text.pdf.PdfPKCS7").getDeclaredField("digest");"
                    digestField.setAccessible(true);
                    pkcs1Object = digestField.get(pcks7);
                }
                catch (final Exception e) {
                    LOGGER.severe(
                      "No se ha podido obtener informacion de una de las firmas del PDF, se continuara con la siguiente: " + e //$NON-NLS-1$
                    );
                    continue;
                }
                if (pkcs1Object instanceof byte[]) {
                    ssi.setPkcs1((byte[]) pkcs1Object);
                }
                root.add(new AOTreeNode(ssi));
            }
            else {
                root.add(new AOTreeNode(AOUtil.getCN(pcks7.getSigningCertificate())));
            }
        }

        return new AOTreeModel(root, root.getChildCount());
    }

    /** Comprueba que los datos proporcionados sean un documento PDF.
     * @param data Datos a comprobar
     * @return <code>true</code> si los datos proporcionados son un documento PDF, <code>false</code> en caso contrario */
    @Override
	public boolean isSign(final byte[] data) {
        if (data == null) {
            LOGGER.warning("Se han introducido datos nulos para su comprobacion"); //$NON-NLS-1$
            return false;
        }
        if (!isPdfFile(data)) {
        	return false;
        }
        return getSignersStructure(data, false).getCount().intValue() > 0;
    }

    @SuppressWarnings("unused")
    private boolean isPdfFile(final byte[] data) {

    	checkIText();

        byte[] buffer = new byte[PDF_FILE_HEADER.length()];
        try {
            new ByteArrayInputStream(data).read(buffer);
        }
        catch (final Exception e) {
            buffer = null;
        }

        // Comprobamos que cuente con una cabecera PDF
        if (buffer != null && !PDF_FILE_HEADER.equals(new String(buffer))) {
            return false;
        }

        try {
            // Si lanza una excepcion al crear la instancia, no es un fichero PDF
            new PdfReader(data);
        }
        catch (final BadPasswordException e) {
            LOGGER.warning("El PDF esta protegido con contrasena, se toma como PDF valido"); //$NON-NLS-1$
            return true;
        }
        catch (final Exception e) {
            return false;
        }

        return true;
    }

    /** Comprueba que los datos proporcionados sean un documento PDF.
     * @param data Datos a comprobar
     * @return <code>true</code> si los datos proporcionados son un documento PDF, <code>false</code> en caso contrario */
    @Override
	public boolean isValidDataFile(final byte[] data) {
        if (data == null) {
            LOGGER.warning("Se han introducido datos nulos para su comprobacion"); //$NON-NLS-1$
            return false;
        }
        return isPdfFile(data);
    }

    /** Obtiene el nombre con el que deber&iacute;a guardarse un PDF tras ser
     * firmado. B&aacute;sicamente se le anexa el sufijo <i>.signed</i> al
     * nombre original, manteniendo la extensi&oacute;n (se respetan
     * may&uacute;culas y min&uacute;sculas en esta, pero no se admite una
     * extensi&oacute;n con mezcla de ambas).
     * @param originalName Nombre original del fichero PDF.
     * @return Nombre recomendado para el PDF ya firmado. */
    public static String getSignedName(final String originalName) {
        if (originalName == null) {
            return "signed.pdf"; //$NON-NLS-1$
        }
        if (originalName.endsWith(PDF_FILE_SUFFIX)) {
            return originalName.replace(PDF_FILE_SUFFIX, ".signed.pdf"); //$NON-NLS-1$
        }
        if (originalName.endsWith(".PDF")) { //$NON-NLS-1$
            return originalName.replace(".PDF", ".signed.pdf"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return originalName + ".signed.pdf"; //$NON-NLS-1$
    }

    /** Si la entrada es un documento PDF, devuelve el mismo documento PDF.
     * @param sign Documento PDF
     * @return Mismo documento PDF de entrada, sin modificar en ning&uacute; aspecto.
     * @throws AOInvalidFormatException Si los datos de entrada no son un documento PDF. */
    @Override
	public byte[] getData(final byte[] sign) throws AOInvalidFormatException {

        // Si no es una firma PDF valida, lanzamos una excepcion
        if (!isSign(sign)) {
            throw new AOInvalidFormatException("El documento introducido no contiene una firma valida"); //$NON-NLS-1$
        }

        // TODO: Devolver el PDF sin firmar
        return sign;
    }

    /** Si la entrada es un documento PDF, devuelve un objeto <code>AOSignInfo</code>
     * con el formato establecido a <code>AOSignConstants.SIGN_FORMAT_PDF</code>.
     * @param data Documento PDF.
     * @return Objeto <code>AOSignInfo</code> con el formato establecido a <code>AOSignConstants.SIGN_FORMAT_PDF</code>.
     * @throws AOException Si los datos de entrada no son un documento PDF. */
    @Override
	public AOSignInfo getSignInfo(final byte[] data) throws AOException {
        if (data == null) {
            throw new IllegalArgumentException("No se han introducido datos para analizar"); //$NON-NLS-1$
        }

        if (!isSign(data)) {
            throw new AOInvalidFormatException("Los datos introducidos no se corresponden con un objeto de firma"); //$NON-NLS-1$
        }

        return new AOSignInfo(AOSignConstants.SIGN_FORMAT_PDF);
        // Aqui podria venir el analisis de la firma buscando alguno de los
        // otros datos de relevancia que se almacenan en el objeto AOSignInfo
    }

    @SuppressWarnings("static-method")
	private void checkIText() {
        final String itextVersion = Platform.getITextVersion();
        if (!ITEXT_VERSION.equals(itextVersion)) {
            throw new InvalidITextException(ITEXT_VERSION, itextVersion);
        }
    }

}
