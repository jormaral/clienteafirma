/* Copyright (C) 2011 [Gobierno de Espana]
 * This file is part of "Cliente @Firma".
 * "Cliente @Firma" is free software; you can redistribute it and/or modify it under the terms of:
 *   - the GNU General Public License as published by the Free Software Foundation;
 *     either version 2 of the License, or (at your option) any later version.
 *   - or The European Software License; either version 1.1 or (at your option) any later version.
 * Date: 11/01/11
 * You may contact the copyright holder at: soporte.afirma5@mpt.es
 */

/**
 *	M&oacute;dulo de generaci&oacute;n de firmas digitales XAdES.
 *  <p>Tabla de compatibilidad respecto a generaci&oacute;n en cliente de variantes de XAdES:</p>
 *  <p align="center"><table border="1" cellpadding="5">
 *   <tr>
 *    <td>XAdES-BES</td>
 *    <td>XAdES-EPES</dt>
 *    <td>XAdES-T</td>
 *    <td>XAdES-C</td>
 *    <td>XAdES-X</td>
 *    <td>XAdES-XL</td>
 *    <td>XAdES-A</td>
 *   </tr>
 *   <tr>
 *    <td bgcolor="green">Si</td>
 *    <td bgcolor="green">Si</td>
 *    <td bgcolor="red">No</td>
 *    <td bgcolor="red">No</td>
 *    <td bgcolor="red">No</td>
 *    <td bgcolor="red">No</td>
 *    <td bgcolor="red">No</td>
 *   </tr>
 *  </table></p>
 *  <p align="center"><br><img src="doc-files/package-info-1.png"></p>
 *  <p>Este m&oacute;dulo presenta las siguientes dependencias directas de primer nivel:</p>
 *  <ul>
 *   <li>Dependencia con el m&oacute;dulo N&uacute;cleo (<i>afirma-core</i>) del Cliente.</li>
 *   <li>Dependencia con el m&oacute;dulo XML (<i>afirma-crypto-core-xml</i>) del Cliente.</li>
 *   <li>Dependencia con JXAdES revisi&oacute;n 5138 o superior.</li>
 *  </ul>
 *  <p>
 *   Este m&oacute;dulo es compatible con cualquier entorno JSE 1.6 o superior.<br>
 *   Para compatibilidad
 *   con JSE 1.5 es necesario incluir las clases Java contenidas en el "Paquete de compatibilidad
 *   con Java 5" del Ciente e instalar los productos Apache Xalan 2.7.1 o superior y Apache Xerces
 *   2.11.0 o superior como API ENDORSED de Java.<br>
 *   Consulte la p&aacute;gina
 *   <a href="http://docs.oracle.com/javase/1.5.0/docs/guide/standards/index.html">http://docs.oracle.com/javase/1.5.0/docs/guide/standards/index.html</a>
 *   para informaci&oacute;n ampliada sobre los API ENDORSED de Java.
 *  </p>
 *  <p>
 *   Desde este m&oacute;dulo es posible que se realicen llamadas a interfaces gr&aacute;ficas.<br>
 *  </p>
 */
package es.gob.afirma.signers.xades;
