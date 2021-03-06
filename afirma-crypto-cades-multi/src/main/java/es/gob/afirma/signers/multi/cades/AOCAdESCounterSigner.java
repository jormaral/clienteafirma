/* Copyright (C) 2011 [Gobierno de Espana]
 * This file is part of "Cliente @Firma".
 * "Cliente @Firma" is free software; you can redistribute it and/or modify it under the terms of:
 *   - the GNU General Public License as published by the Free Software Foundation;
 *     either version 2 of the License, or (at your option) any later version.
 *   - or The European Software License; either version 1.1 or (at your option) any later version.
 * Date: 11/01/11
 * You may contact the copyright holder at: soporte.afirma5@mpt.es
 */

package es.gob.afirma.signers.multi.cades;

import java.io.IOException;
import java.security.PrivateKey;
import java.util.Properties;

import es.gob.afirma.core.AOException;
import es.gob.afirma.core.misc.MimeHelper;
import es.gob.afirma.core.signers.AOCounterSigner;
import es.gob.afirma.core.signers.AOSignConstants;
import es.gob.afirma.core.signers.AdESPolicy;
import es.gob.afirma.core.signers.CounterSignTarget;
import es.gob.afirma.signers.cades.AOCAdESSigner;
import es.gob.afirma.signers.cades.CAdESValidator;
import es.gob.afirma.signers.pkcs7.P7ContentSignerParameters;
import es.gob.afirma.signers.pkcs7.ReadNodesTree;

/** Operaciones de cofirma CAdES. */
public class AOCAdESCounterSigner implements AOCounterSigner {

	/** {@inheritDoc} */
	@Override
	public byte[] countersign(final byte[] sign,
                              final String algorithm,
                              final CounterSignTarget targetType,
                              final Object[] targets,
                              final PrivateKey key,
                              final java.security.cert.Certificate[] certChain,
                              final Properties xParams) throws AOException, IOException {

        final Properties extraParams = xParams != null ? xParams : new Properties();

        boolean signingCertificateV2;
        if (AOSignConstants.isSHA2SignatureAlgorithm(algorithm)) {
        	signingCertificateV2 = true;
        }
        else if (extraParams.containsKey("signingCertificateV2")) { //$NON-NLS-1$
        	signingCertificateV2 = Boolean.parseBoolean(extraParams.getProperty("signingCertificateV2")); //$NON-NLS-1$
        }
        else {
        	signingCertificateV2 = !"SHA1".equals(AOSignConstants.getDigestAlgorithmName(algorithm));	 //$NON-NLS-1$
        }

        final P7ContentSignerParameters csp = new P7ContentSignerParameters(
    		sign,
    		algorithm
		);

        String contentTypeOid = MimeHelper.DEFAULT_CONTENT_OID_DATA;
        String contentDescription = MimeHelper.DEFAULT_CONTENT_DESCRIPTION;
        final byte[] data = new AOCAdESSigner().getData(sign);
        if (data != null) {
        	final MimeHelper mimeHelper = new MimeHelper(data);
			contentDescription = mimeHelper.getDescription();
			contentTypeOid = MimeHelper.transformMimeTypeToOid(mimeHelper.getMimeType());
        }

        // Datos firmados.
        byte[] dataSigned = null;
        // Si la firma que nos introducen es SignedData
        if (CAdESValidator.isCAdESSignedData(sign)) {
            try {
                // CASO DE FIRMA DE ARBOL
                if (targetType == CounterSignTarget.TREE) {
                    final int[] nodes = {
                        0
                    };

                    dataSigned = new CAdESCounterSigner().counterSigner(
                    	   csp,
	                       sign,
	                       CounterSignTarget.TREE,
	                       nodes,
	                       key,
	                       certChain,
	                       new AdESPolicy(extraParams),
	                       signingCertificateV2,
	                       contentTypeOid,
	                       contentDescription
                    );
                }
                // CASO DE FIRMA DE HOJAS
                else if (targetType == CounterSignTarget.LEAFS) {
                    final int[] nodes = {
                        0
                    };
                    dataSigned =
                            new CAdESCounterSigner().counterSigner(
                        		csp,
                                sign,
                                CounterSignTarget.LEAFS,
                                nodes,
                                key,
                                certChain,
                                new AdESPolicy(extraParams),
								signingCertificateV2,
                                contentTypeOid,
                                contentDescription
                    		);
                }
                // CASO DE FIRMA DE NODOS
                else if (targetType == CounterSignTarget.NODES) {
                    int[] nodesID = new int[targets.length];
                    for (int i = 0; i < targets.length; i++) {
                        nodesID[i] = ((Integer) targets[i]).intValue();
                    }
					nodesID = ReadNodesTree.simplyArray(nodesID);
                    dataSigned =
                            new CAdESCounterSigner().counterSigner(
                        		csp,
                                sign,
                                CounterSignTarget.NODES,
                                nodesID,
                                key,
                                certChain,
                                new AdESPolicy(extraParams),
								signingCertificateV2,
                                contentTypeOid,
                                contentDescription
                            );
                }
                // CASO DE FIRMA DE NODOS DE UNO O VARIOS FIRMANTES
                else if (targetType == CounterSignTarget.SIGNERS) {

                    // clase que lee los nodos de un fichero firmado (p7s, csig,
                    // sig)
                    final String[] signers = new String[targets.length];
                    for (int i = 0; i < targets.length; i++) {
                        signers[i] = (String) targets[i];
                    }
                    final int[] nodes2 = new ReadNodesTree().readNodesFromSigners(signers, sign);
                    dataSigned =
                            new CAdESCounterSigner().counterSigner(
                        		csp,
                                sign,
                                CounterSignTarget.SIGNERS,
                                nodes2,
                                key,
                                certChain,
                                new AdESPolicy(extraParams),
                                signingCertificateV2,
                                contentTypeOid,
                                contentDescription
                    		);

                }

                return dataSigned;

            }
            catch (final Exception e) {
                throw new AOException("Error generando la Contrafirma CAdES", e); //$NON-NLS-1$
            }
        }
        // Signed and enveloped

        try {
            // CASO DE FIRMA DE ARBOL
            if (targetType == CounterSignTarget.TREE) {
                final int[] nodes = {
                    0
                };

                dataSigned =
                        new CAdESCounterSignerEnveloped().counterSigner(
                    		csp,
                            sign,
                            CounterSignTarget.TREE,
                            nodes,
                            key,
                            certChain,
                            new AdESPolicy(extraParams),
                            signingCertificateV2,
                            contentTypeOid,
                            contentDescription
                        );
            }
            // CASO DE FIRMA DE HOJAS
            else if (targetType == CounterSignTarget.LEAFS) {
                final int[] nodes = {
                    0
                };
                dataSigned =
                        new CAdESCounterSignerEnveloped().counterSigner(
                    		csp,
                            sign,
                            CounterSignTarget.LEAFS,
                            nodes,
                            key,
                            certChain,
                            new AdESPolicy(extraParams),
                            signingCertificateV2,
                            contentTypeOid,
                            contentDescription
                		);
            }
            // CASO DE FIRMA DE NODOS
            else if (targetType == CounterSignTarget.NODES) {
                int[] nodesID = new int[targets.length];
                for (int i = 0; i < targets.length; i++) {
                    nodesID[i] = ((Integer) targets[i]).intValue();
                }
				nodesID = ReadNodesTree.simplyArray(nodesID);
                dataSigned = new CAdESCounterSignerEnveloped().counterSigner(
            		csp,
                    sign,
                    CounterSignTarget.NODES,
                    nodesID,
                    key,
                    certChain,
                    new AdESPolicy(extraParams),
                    signingCertificateV2,
                    contentTypeOid,
                    contentDescription
                );
            }
            // CASO DE FIRMA DE NODOS DE UNO O VARIOS FIRMANTES
            else if (targetType == CounterSignTarget.SIGNERS) {

                // clase que lee los nodos de un fichero firmado (p7s, csig,
                // sig)
                final String[] signers = new String[targets.length];
                for (int i = 0; i < targets.length; i++) {
                    signers[i] = (String) targets[i];
                }
                final int[] nodes2 = new ReadNodesTree().readNodesFromSigners(signers, sign);
                dataSigned = new CAdESCounterSignerEnveloped().counterSigner(
            		csp,
                    sign,
                    CounterSignTarget.SIGNERS,
                    nodes2,
                    key,
                    certChain,
                    new AdESPolicy(extraParams),
                    signingCertificateV2,
                    contentTypeOid,
                    contentDescription
                );
            }

            return dataSigned;

        }
        catch (final Exception e) {
            throw new AOException("Error generando la Contrafirma CAdES", e); //$NON-NLS-1$
        }
    }

}
