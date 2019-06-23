package ch.elexis.core.services;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Component;

import ch.elexis.core.constants.Preferences;
import ch.elexis.core.constants.StringConstants;
import ch.elexis.core.model.FallConstants;
import ch.elexis.core.model.IContact;
import ch.elexis.core.model.ICoverage;
import ch.elexis.core.services.holder.BillingSystemServiceHolder;
import ch.elexis.core.services.holder.ConfigServiceHolder;
import ch.elexis.core.services.holder.ContextServiceHolder;
import ch.elexis.core.services.holder.CoreModelServiceHolder;
import ch.rgw.tools.StringTool;

@Component
public class CoverageService implements ICoverageService {
	
	@Override
	public boolean isValid(ICoverage coverage){
		if (coverage.getPatient() == null) {
			return false;
		}
		
		// Check whether all user-defined requirements for this billing system
		// are met
		String reqs = BillingSystemServiceHolder.get().getRequirements(coverage.getBillingSystem());
		if (reqs != null) {
			for (String req : reqs.split(";")) { //$NON-NLS-1$
				String localReq = ""; //$NON-NLS-1$
				String[] r = req.split(":"); //$NON-NLS-1$
				if ((r[1].equalsIgnoreCase("X")) && (r.length > 2)) { //$NON-NLS-1$
					// *** support for additional field types (checkboxes with
					// multiple items are
					// special)
					String[] items = r[2].split("\t"); //$NON-NLS-1$
					if (items.length > 1) {
						for (int rIx = 0; rIx < items.length; rIx++) {
							localReq = (String) coverage.getExtInfo(r[0] + "_" + items[rIx]); //$NON-NLS-1$
							if (StringTool.isNothing(localReq)) {
								return false;
							}
						}
					}
				} else {
					localReq = (String) coverage.getExtInfo(r[0]);
					if (StringTool.isNothing(localReq)) {
						return false;
					}
				}
				if (r[1].equals("K")) { //$NON-NLS-1$
					Optional<IContact> contact =
						CoreModelServiceHolder.get().load(localReq, IContact.class);
					if (!contact.isPresent()) {
						return false;
					}
				}
			}
		}
		// check whether the outputter could output a bill
		// TODO check if this should be enabled, BillingSystem#getDefaultPrintSystem never returned null
		//		if (BillingSystemServiceHolder.get()
		//			.getDefaultPrintSystem(coverage.getBillingSystem()) == null) {
		//			return false;
		//		}
		//		IRnOutputter outputter = getOutputter();
		//		if (outputter == null) {
		//			return false;
		//		} else {
		//			if (!outputter.canBill(this)) {
		//				return false;
		//			}
		//		}
		return true;
	}
	
	@Override
	public String getRequiredString(ICoverage coverage, String name){
		String value = (String) coverage.getExtInfo(name);
		if (StringUtils.isBlank(value)) {
			value = BillingSystemServiceHolder.get()
				.getBillingSystemConstant(coverage.getBillingSystem(), name);
		}
		return value;
	}
	
	@Override
	public void setRequiredString(ICoverage coverage, String name, String value){
		String requirements =
			BillingSystemServiceHolder.get().getRequirements(coverage.getBillingSystem());
		String[] req = requirements.split(";"); //$NON-NLS-1$
		int idx = StringTool.getIndex(req, name + ":T"); //$NON-NLS-1$
		if (idx != -1) {
			coverage.setExtInfo(name, value);
		}
	}
	
	@Override
	public IContact getRequiredContact(ICoverage coverage, String name){
		String id = (String) coverage.getExtInfo(name);
		if (StringUtils.isBlank(id)) {
			return null;
		}
		return CoreModelServiceHolder.get().load(id, IContact.class).orElse(null);
	}
	
	@Override
	public void setRequiredContact(ICoverage coverage, String name, IContact value){
		String requirements =
			BillingSystemServiceHolder.get().getRequirements(coverage.getBillingSystem());
		if (!StringTool.isNothing(requirements)) {
			String[] req = requirements.split(";"); //$NON-NLS-1$
			int idx = StringTool.getIndex(req, name + ":K"); //$NON-NLS-1$
			if (idx != -1) {
				if (req[idx].endsWith(":K")) { //$NON-NLS-1$
					coverage.setExtInfo(name, value.getId());
				}
			}
		}
	}
	
	@Override
	public Tiers getTiersType(ICoverage coverage){
		IContact costBearer = coverage.getCostBearer();
		IContact guarantor = coverage.getGuarantor();
		if (costBearer != null && costBearer.isOrganization()) {
			if (guarantor.equals(costBearer)) {
				return Tiers.PAYANT;
			}
		}
		return Tiers.GARANT;
	}
	
	@Override
	public boolean getCopyForPatient(ICoverage coverage){
		return StringConstants.ONE
			.equals(coverage.getExtInfo(FallConstants.FLD_EXT_COPY_FOR_PATIENT));
	}
	
	@Override
	public void setCopyForPatient(ICoverage coverage, boolean copy){
		coverage.setExtInfo(FallConstants.FLD_EXT_COPY_FOR_PATIENT,
			copy ? StringConstants.ONE : StringConstants.ZERO);
	}
	
	/**
	 * Get the default label for a new {@link ICoverage}. Performs a lookup for
	 * {@link Preferences#USR_DEFCASELABEL} configuration.
	 * 
	 * @return
	 */
	public String getDefaultCoverageLabel(){
		Optional<IContact> userContact = ContextServiceHolder.get().getActiveUserContact();
		if (userContact.isPresent()) {
			return ConfigServiceHolder.get().get(userContact.get(), Preferences.USR_DEFCASELABEL,
				Preferences.USR_DEFCASELABEL_DEFAULT);
		}
		return Preferences.USR_DEFCASELABEL_DEFAULT;
	}
	
	/**
	 * Get the default reason for a new {@link ICoverage}. Performs a lookup for
	 * {@link Preferences#USR_DEFCASEREASON} configuration.
	 * 
	 * @return
	 */
	public String getDefaultCoverageReason(){
		Optional<IContact> userContact = ContextServiceHolder.get().getActiveUserContact();
		if (userContact.isPresent()) {
			return ConfigServiceHolder.get().get(userContact.get(), Preferences.USR_DEFCASEREASON,
				Preferences.USR_DEFCASEREASON_DEFAULT);
		}
		return Preferences.USR_DEFCASEREASON_DEFAULT;
	}
	
	/**
	 * Get the default law for a new {@link ICoverage}. Performs a lookup for
	 * {@link Preferences#USR_DEFLAW} configuration.
	 * 
	 * TODO implement BillingSystem
	 * 
	 * @return
	 */
	public String getDefaultCoverageLaw(){
		Optional<IContact> userContact = ContextServiceHolder.get().getActiveUserContact();
		if (userContact.isPresent()) {
			return ConfigServiceHolder.get().get(userContact.get(), Preferences.USR_DEFLAW,
				"defaultBillingSystem");
		}
		return "defaultBillingSystem";
		//		return CoreHub.userCfg.get(Preferences.USR_DEFLAW,
		//			BillingSystem.getAbrechnungsSysteme()[0]);
	}
}
