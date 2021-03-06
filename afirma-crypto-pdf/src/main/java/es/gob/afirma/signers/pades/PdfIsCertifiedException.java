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

import es.gob.afirma.core.AOException;

/** Indica que el PDF no ha podido firmarse por estar certificado.
 * @author Tom&aacute;s Garc&iacute;a-Mer&aacute;s */
public class PdfIsCertifiedException extends AOException {

	private static final long serialVersionUID = -7345078395676697490L;

	/** Crea una excepci&oacute;n que indica que el PDF no ha podido firmarse por estar certificado.
	 * @param e Causa inicial de la excepci&oacute;n */
	PdfIsCertifiedException() {
		super("El PDF no se ha firmado por estar certificado"); //$NON-NLS-1$
	}

}
