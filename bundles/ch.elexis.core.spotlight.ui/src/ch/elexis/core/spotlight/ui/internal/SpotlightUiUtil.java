package ch.elexis.core.spotlight.ui.internal;

import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;

import javax.inject.Inject;

import org.eclipse.e4.ui.workbench.modeling.EPartService;
import org.eclipse.e4.ui.workbench.modeling.EPartService.PartState;
import org.eclipse.swt.program.Program;
import org.slf4j.LoggerFactory;

import ch.elexis.core.documents.DocumentStore;
import ch.elexis.core.exceptions.ElexisException;
import ch.elexis.core.model.IAppointment;
import ch.elexis.core.model.IContact;
import ch.elexis.core.model.IDocument;
import ch.elexis.core.model.IEncounter;
import ch.elexis.core.model.IPatient;
import ch.elexis.core.services.IContextService;
import ch.elexis.core.services.holder.CoreModelServiceHolder;
import ch.elexis.core.spotlight.ISpotlightResultEntry;
import ch.elexis.core.spotlight.ISpotlightResultEntry.Category;

public class SpotlightUiUtil {
	
	@Inject
	private IContextService contextService;
	
	@Inject
	private DocumentStore documentStore;
	
	private EPartService partService;
	
	public SpotlightUiUtil(EPartService partService){
		this.partService = partService;
	}
	
	private boolean handleEnter(ISpotlightResultEntry selected){
		if (selected == null) {
			return false;
		}
		
		Category category = selected.getCategory();
		String objectId = selected.getLoaderString();
		
		switch (category) {
		case PATIENT:
			IPatient patient =
				CoreModelServiceHolder.get().load(objectId, IPatient.class).orElse(null);
			contextService.setActivePatient(patient);
			return true;
		case DOCUMENT:
			IDocument document = documentStore
				.loadDocument(objectId, documentStore.getDefaultDocumentStore().getId())
				.orElse(null);
			if (document != null) {
				try {
					File tmpFile = File.createTempFile("doc", document.getExtension());
					tmpFile.deleteOnExit();
					documentStore.saveContentToFile(document, tmpFile.getAbsolutePath());
					Program program = Program.findProgram(document.getExtension());
					if (program != null) {
						program.execute(tmpFile.getAbsolutePath());
					}
				} catch (IOException | ElexisException e) {
					LoggerFactory.getLogger(getClass()).info("Exception opening document [{}]",
						objectId, e);
				}
			}
			return true;
		case ENCOUNTER:
			IEncounter encounter =
				CoreModelServiceHolder.get().load(objectId, IEncounter.class).orElse(null);
			return handleEnter(encounter);
		default:
			System.out.println("No default enter action");
			return false;
		}
	}
	
	private boolean handleEnter(IEncounter encounter){
		if (encounter != null) {
			contextService.getRootContext().setTyped(encounter.getPatient());
			contextService.getRootContext().setTyped(encounter);
			partService.showPart("ch.elexis.Konsdetail", PartState.ACTIVATE);
			return true;
		}
		return false;
	}
	
	private boolean handleEnter(IAppointment appointment){
		if (appointment != null) {
			IContact contact = appointment.getContact();
			if (contact != null) {
				IPatient patient =
					CoreModelServiceHolder.get().load(contact.getId(), IPatient.class).orElse(null);
				contextService.setActivePatient(patient);
				return true;
			}
		}
		return false;
	}
	
	private boolean handleEnter(String string){
		if (string.startsWith(Category.PATIENT.name())) {
			IPatient patient = CoreModelServiceHolder.get()
				.load(string.substring(Category.PATIENT.name().length() + 2), IPatient.class)
				.orElse(null);
			contextService.setActivePatient(patient);
			return patient != null;
		} else if (string.startsWith("balance")) {
			// show patients balance
			// expects balance::patientId
			boolean ok = handleEnter(
				Category.PATIENT.name() + "::" + string.substring("balance".length() + 2));
			if (ok) {
				// switched to patient
				partService.showPart("ch.elexis.core.ui.views.rechnung.InvoiceListView",
					PartState.ACTIVATE);
				return true;
			}
			return false;
		}
		return false;
	}
	
	@SuppressWarnings("unchecked")
	public boolean handleEnter(Object selectedElement){
		if (selectedElement instanceof IAppointment) {
			return handleEnter((IAppointment) selectedElement);
		} else if (selectedElement instanceof IEncounter) {
			return handleEnter((IEncounter) selectedElement);
		} else if (selectedElement instanceof ISpotlightResultEntry) {
			return handleEnter((ISpotlightResultEntry) selectedElement);
		} else if (selectedElement instanceof Supplier<?>) {
			return ((Supplier<Boolean>) selectedElement).get();
		} else if (selectedElement instanceof String) {
			return handleEnter((String) selectedElement);
		}
		return false;
	}
	
}
