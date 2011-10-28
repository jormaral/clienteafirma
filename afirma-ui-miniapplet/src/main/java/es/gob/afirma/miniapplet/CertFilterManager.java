package es.gob.afirma.miniapplet;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import es.gob.afirma.keystores.filters.CertificateFilter;
import es.gob.afirma.keystores.filters.DNIeFilter;

public class CertFilterManager {

	private final static String FILTER_PREFIX_KEY = "filter"; //$NON-NLS-1$
	
	private final static String FILTER_TYPE_DNIE = "dnie:"; //$NON-NLS-1$
	
	private final static String FILTER_TYPE_DNI = "dni:"; //$NON-NLS-1$
	
	private final static String FILTER_TYPE_SSL = "ssl:"; //$NON-NLS-1$
	
	private boolean mandatoryCertificate = false;
	
	private List<CertificateFilter> filters;
	
	/**
	 * Identifica los filtros que deben aplicarse sobre una serie de certificados para
	 * comprobar cuales de ellos se ajustan a nuestra necesidades. 
	 * @param propertyFilters Listado de propiedades entre las que identificar las que
	 * establecen los criterios de filtrado.
	 */
	public CertFilterManager(Properties propertyFilters) {
		
		String filterValue = propertyFilters.getProperty(FILTER_PREFIX_KEY);
		if (filterValue == null) {
			return;
		}
		
		CertificateFilter filter;
		if (filterValue.toLowerCase().startsWith(FILTER_TYPE_DNIE)) {
			filter = new DNIeFilter();
//		} else if (filterValue.toLowerCase().startsWith(FILTER_TYPE_DNI)) {
//			filter = new DNIFilter();
//		} else if (filterValue.toLowerCase().startsWith(FILTER_TYPE_SSL)) {
//			filter = new SSLFilter();
		} else {
			return;
		}
		
		this.filters = new ArrayList<CertificateFilter>();
		this.filters.add(filter);
		
		this.mandatoryCertificate = true;
	}
	
	/**
	 * Devuelve la lista de certificados definidos.
	 * @return Listado de certificados.
	 */
	public List<CertificateFilter> getFilters() {
		return (this.filters != null ? new ArrayList<CertificateFilter>(this.filters) : null);
	}
	
	/**
	 * Indica si se debe seleccionar autom&aacute;ticamente un certificado si es el &uacute;nico que
	 * cumple los filtros. 
	 * @return {@code true} si debe seleccionarse autom&aacute;ticamente el &uacute;nico certificado
	 * que supera el filtrado, {@code false} en caso contrario.
	 */
	public boolean isMandatoryCertificate() {
		return this.mandatoryCertificate;
	}
	
	
}