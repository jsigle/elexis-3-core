/*******************************************************************************
 * Copyright (c) 2005-2010, G. Weirich and Elexis, Portions (c) 2012-2021, Joerg M. Sigle (js, jsigle)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    G. Weirich - initial implementation
 *    Joerg Sigle - Added ability to copy selected addresses to the clipboard
 *    Joerg Sigle - Added ability to automatically tidy contact data with delta export
 * 
 *******************************************************************************/

package ch.elexis.core.ui.contacts.views;

import java.util.HashMap;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.ISaveablePart2;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;

import ch.elexis.admin.AccessControlDefaults;
import ch.elexis.core.constants.StringConstants;
import ch.elexis.core.data.events.ElexisEventDispatcher;
import ch.elexis.core.data.status.ElexisStatus;
import ch.elexis.core.ui.UiDesk;
import ch.elexis.core.ui.actions.FlatDataLoader;
import ch.elexis.core.ui.actions.GlobalActions;
import ch.elexis.core.ui.actions.PersistentObjectLoader;
import ch.elexis.core.ui.contacts.Activator;
import ch.elexis.core.ui.dialogs.GenericPrintDialog;
import ch.elexis.core.ui.dialogs.KontaktErfassenDialog;
import ch.elexis.core.ui.icons.Images;
import ch.elexis.core.ui.locks.LockedRestrictedAction;
import ch.elexis.core.ui.util.SWTHelper;
import ch.elexis.core.ui.util.ViewMenus;
import ch.elexis.core.ui.util.viewers.CommonViewer;
import ch.elexis.core.ui.util.viewers.DefaultControlFieldProvider;
import ch.elexis.core.ui.util.viewers.DefaultLabelProvider;
import ch.elexis.core.ui.util.viewers.SimpleWidgetProvider;
import ch.elexis.core.ui.util.viewers.ViewerConfigurer;
import ch.elexis.core.ui.util.viewers.ViewerConfigurer.ControlFieldListener;
import ch.elexis.core.ui.views.Messages;
import ch.elexis.data.Kontakt;
import ch.elexis.data.Organisation;
import ch.elexis.data.PersistentObject;
import ch.elexis.data.Person;
import ch.elexis.data.Query;
import ch.rgw.tools.StringTool;
import ch.rgw.tools.TimeTool;

public class KontakteView extends ViewPart implements ControlFieldListener, ISaveablePart2 {
	public static final String ID = "ch.elexis.Kontakte"; //$NON-NLS-1$
	private CommonViewer cv;
	private ViewerConfigurer vc;
	IAction dupKontakt, delKontakt, createKontakt, printList,
			tidySelectedAddressesAction,				//201303041746js: Added tidySelectedAddressesAction
			copySelectedContactInfosToClipboardAction,	//201202161250js: Added copySelectedContactInfosToClipboardAction
			copySelectedAddressesToClipboardAction;		//201201280152js: Added copySelectedAddressesToClipboardAction
	
	PersistentObjectLoader loader;

	private final String[] fields = { Kontakt.FLD_SHORT_LABEL + Query.EQUALS + Messages.KontakteView_shortLabel, // $NON-NLS-1$
			Kontakt.FLD_NAME1 + Query.EQUALS + Messages.KontakteView_text1, // $NON-NLS-1$
			Kontakt.FLD_NAME2 + Query.EQUALS + Messages.KontakteView_text2, // $NON-NLS-1$
			Kontakt.FLD_STREET + Query.EQUALS + Messages.KontakteView_street, // $NON-NLS-1$
			Kontakt.FLD_ZIP + Query.EQUALS + Messages.KontakteView_zip, // $NON-NLS-1$
			Kontakt.FLD_PLACE + Query.EQUALS + Messages.KontakteView_place }; // $NON-NLS-1$
	private ViewMenus menu;

	public KontakteView() {
	}

	@Override
	public void createPartControl(Composite parent) {
		parent.setLayout(new FillLayout());
		cv = new CommonViewer();
		loader = new FlatDataLoader(cv, new Query<Kontakt>(Kontakt.class));
		loader.setOrderFields(
				new String[] { Kontakt.FLD_NAME1, Kontakt.FLD_NAME2, Kontakt.FLD_STREET, Kontakt.FLD_PLACE });
		vc = new ViewerConfigurer(loader, new KontaktLabelProvider(), new DefaultControlFieldProvider(cv, fields),
				new ViewerConfigurer.DefaultButtonProvider(),
				new SimpleWidgetProvider(SimpleWidgetProvider.TYPE_LAZYLIST, SWT.MULTI, null));
		cv.create(vc, parent, SWT.NONE, getViewSite());
		makeActions();
		cv.setObjectCreateAction(getViewSite(), createKontakt);
		menu = new ViewMenus(getViewSite());
		menu.createViewerContextMenu(cv.getViewerWidget(), delKontakt, dupKontakt);
		
		menu.createMenu(printList);
		menu.createMenu(copySelectedContactInfosToClipboardAction);			//201202161250js: Added copySelectedContactInfosToClipboardAction
		menu.createMenu(copySelectedAddressesToClipboardAction);			//201201280152js: Added copySelectedAddressesToClipboardAction
		menu.createMenu(tidySelectedAddressesAction);						//201303041746js: Added tidySelectedAddressesAction
		
		menu.createToolbar(printList);
		menu.createToolbar(copySelectedContactInfosToClipboardAction);		//201202161220js: Added copySelectedContactInfosToClipboardAction
		menu.createToolbar(copySelectedAddressesToClipboardAction);			//201201280152js: Added copySelectedAddressesToClipboardAction
		menu.createToolbar(tidySelectedAddressesAction);					//201303041746js: Added tidySelectedAddressesAction
		
		vc.getContentProvider().startListening();
		vc.getControlFieldProvider().addChangeListener(this);
		cv.addDoubleClickListener(new CommonViewer.DoubleClickListener() {
			public void doubleClicked(PersistentObject obj, CommonViewer cv) {
				try {
					KontaktDetailView kdv = (KontaktDetailView) getSite().getPage().showView(KontaktDetailView.ID);
					ElexisEventDispatcher.fireSelectionEvent(obj);
					//					kdv.kb.catchElexisEvent(new ElexisEvent(obj, obj.getClass(), ElexisEvent.EVENT_SELECTED));
				} catch (PartInitException e) {
					ElexisStatus es = new ElexisStatus(ElexisStatus.ERROR, Activator.PLUGIN_ID, ElexisStatus.CODE_NONE,
							"Fehler beim Öffnen", e);
					ElexisEventDispatcher.fireElexisStatusEvent(es);
				}

			}
		});
	}

	public void dispose() {
		vc.getContentProvider().stopListening();
		vc.getControlFieldProvider().removeChangeListener(this);
		super.dispose();
	}

	@Override
	public void setFocus() {
		vc.getControlFieldProvider().setFocus();
	}

	public void changed(HashMap<String, String> values) {
		ElexisEventDispatcher.clearSelection(Kontakt.class);
	}

	public void reorder(String field) {
		loader.reorder(field);
	}

	/**
	 * ENTER has been pressed in the control fields,
	 * select the first listed patient
	 */
	// this is also implemented in PatientenListeView
	public void selected() {
		StructuredViewer viewer = cv.getViewerWidget();
		Object[] elements = cv.getConfigurer().getContentProvider().getElements(viewer.getInput());

		if (elements != null && elements.length > 0) {
			Object element = elements[0];
			/*
			 * just selecting the element in the viewer doesn't work if the
			 * control fields are not empty (i. e. the size of items changes):
			 * cv.setSelection(element, true); bug in TableViewer with style
			 * VIRTUAL? work-arount: just globally select the element without
			 * visual representation in the viewer
			 */
			if (element instanceof PersistentObject) {
				// globally select this object
				ElexisEventDispatcher.fireSelectionEvent((PersistentObject) element);
			}
		}
	}

	/*
	 * Die folgenden 6 Methoden implementieren das Interface ISaveablePart2 Wir
	 * benötigen das Interface nur, um das Schliessen einer View zu verhindern,
	 * wenn die Perspektive fixiert ist. Gibt es da keine einfachere Methode?
	 */
	public int promptToSaveOnClose() {
		return GlobalActions.fixLayoutAction.isChecked() ? ISaveablePart2.CANCEL : ISaveablePart2.NO;
	}

	public void doSave(IProgressMonitor monitor) { /* leer */
	}

	public void doSaveAs() { /* leer */
	}

	public boolean isDirty() {
		return true;
	}

	public boolean isSaveAsAllowed() {
		return false;
	}

	public boolean isSaveOnCloseNeeded() {
		return true;
	}

	private void makeActions() {
		delKontakt = new LockedRestrictedAction<Kontakt>(AccessControlDefaults.KONTAKT_DELETE,
				Messages.KontakteView_delete) {
			@Override
			public void doRun(Kontakt k) {
				if (SWTHelper.askYesNo("Wirklich löschen?", k.getLabel())) {
					k.delete();
					cv.getConfigurer().getControlFieldProvider().fireChangedEvent();
				}
			}

			@Override
			public Kontakt getTargetedObject() {
				return (Kontakt) cv.getViewerWidgetFirstSelection();
			}
		};
		dupKontakt = new Action(Messages.KontakteView_duplicate) { // $NON-NLS-1$
			@Override
			public void run() {
				Object[] o = cv.getSelection();
				if (o != null) {
					Kontakt k = (Kontakt) o[0];
					Kontakt dup;
					if (k.istPerson()) {
						Person p = Person.load(k.getId());
						dup = new Person(p.getName(), p.getVorname(), p.getGeburtsdatum(), p.getGeschlecht());
					} else {
						Organisation org = Organisation.load(k.getId());
						dup = new Organisation(org.get(Organisation.FLD_NAME1), org.get(Organisation.FLD_NAME2));
					}
					dup.setAnschrift(k.getAnschrift());
					cv.getConfigurer().getControlFieldProvider().fireChangedEvent();
					// cv.getViewerWidget().refresh();
				}
			}
		};
		createKontakt = new Action(Messages.KontakteView_create) { // $NON-NLS-1$
			@Override
			public void run() {
				String[] flds = cv.getConfigurer().getControlFieldProvider().getValues();
				String[] predef = new String[] { flds[1], flds[2], StringConstants.EMPTY, flds[3], flds[4], flds[5] };
				KontaktErfassenDialog ked = new KontaktErfassenDialog(getViewSite().getShell(), predef);
				ked.open();
			}
		};

		printList = new Action("Markierte Adressen drucken") {
			{
				setImageDescriptor(Images.IMG_PRINTER.getImageDescriptor());
				setToolTipText("Die in der Liste markierten Kontakte als Tabelle ausdrucken");
			}

			public void run() {
				Object[] sel = cv.getSelection();
				String[][] adrs = new String[sel.length][];
				if (sel != null && sel.length > 0) {
					GenericPrintDialog gpl = new GenericPrintDialog(getViewSite().getShell(), "Adressliste",
							"Adressliste");
					gpl.create();
					for (int i = 0; i < sel.length; i++) {
						Kontakt k = (Kontakt) sel[i];
						
						//The following is a beautiful example of hardly self-explanatory code.
						//I've added comments to explain.
						
						//20210325js: Change output format upon request by JH
						//to include the professional title,
						//show the first name before the family name,
						//and add a few more details
						//Leider kann man nicht einfach zusätzlich Kontakt.FLD_TITLE oder so laden
						
						//String[] f = new String[] { Kontakt.FLD_NAME1, Kontakt.FLD_NAME2, Kontakt.FLD_NAME3,
						//		Kontakt.FLD_STREET, Kontakt.FLD_ZIP, Kontakt.FLD_PLACE, Kontakt.FLD_PHONE1 };

						//Sadly, Title comes out empty with this approach:
						//String[] f = new String[] { Kontakt.FLD_NAME1, Kontakt.FLD_NAME2, Kontakt.FLD_NAME3,
						//		Kontakt.FLD_STREET, Kontakt.FLD_ZIP, Kontakt.FLD_PLACE, Kontakt.FLD_PHONE1, 
						//		Kontakt.FLD_FAX, Kontakt.FLD_E_MAIL, Person.TITLE};
						
						//Try this - does this contain the title? Nope.
						//Kontakt.FLD_SHORT_LABEL	= Patientennummer oder Kürzel
						//So we cannot get the title directly out of Kontakt. Solution see below.
						
						String[] f = new String[] { Kontakt.FLD_NAME1, Kontakt.FLD_NAME2, Kontakt.FLD_NAME3,
								Kontakt.FLD_STREET, Kontakt.FLD_ZIP, Kontakt.FLD_PLACE, Kontakt.FLD_PHONE1, 
								Kontakt.FLD_FAX, Kontakt.FLD_E_MAIL};
						
						//To understand what's really in f[] now:
						//System.out.println("Printing an address list with the following components: ");
						//System.out.println(f[0]);	//Bezeichnung1
						//System.out.println(f[1]);	//Bezeichnung2
						//System.out.println(f[2]);	//Bezeichnung3
						//System.out.println(f[3]);	//Strasse
						//System.out.println(f[4]);	//PLZ
						//System.out.println(f[5]);	//Ort
						//System.out.println(f[6]);	//Telefon1
						//The mod by js above above added:
						//System.out.println(f[7]);	//Fax
						//System.out.println(f[8]);	//E-Mail
						
						//f probably means "field(-name)", and v probably means "value"
						//k is "kontakt" and p is "person", both are objects obtained from the db
						//adrs[] probably means "address" and refers to a line in the output table
						
						String[] v = new String[f.length];
						k.get(f, v);
						
						/*
						 * 20210325js: Adding comments explaining this code...
						 * Original version, producing 4 columns in a table
						 *
						//i is the currently processed entry from the list of selected contacts
						//create an array of 4 strings for the currently processed entry
						//these will be output as 4 columns in the current row of the resulting table
						adrs[i] = new String[4];
						//into field 0, put Bezeichnung1=Name SPACE Bezeichnung2=Vorname SPACE Bezeichnung3=Zusatz
						adrs[i][0] = new StringBuilder(v[0]).append(StringConstants.SPACE).append(v[1])
								.append(StringConstants.SPACE).append(v[2]).toString();
						//into field 1, put Strasse
						adrs[i][1] = v[3];
						//into field 2, put PLZ SPACE Ort
						adrs[i][2] = new StringBuilder(v[4]).append(StringConstants.SPACE).append(v[5]).toString();
						//into field 3, put Telefon1
						adrs[i][3] = v[6];
						 *
						 */
						
						/*
						 * 20210325js:
						 * jsversion, producing 7 columns in a table, names ordered the other way round
						 * Sadly, the Title stays empty, the Fax maybe as well (never mind),
						 * and all the fields combined are too long for a single line.
						 * Consequentially, the names are wrapped within their little field,
						 * and this looks even worse than the original.
						 *
						//i is the currently processed entry from the list of selected contacts
						//create an array of 4 strings for the currently processed entry
						//these will be output as 4 columns in the current row of the resulting table
						adrs[i] = new String[8];
						//into field 0, put Title
						//The professional title must be obtained in a slightly complicated way.
						if (k.istPerson()) {
							Person p = Person.load(k.getId());
							adrs[i][0] new StringBuilder(p.get(Person.TITLE)).toString();
							};
						//into field 1, put Bezeichnung1 SPACE Bezeichnung2 SPACE Bezeichnung3
						adrs[i][1] = new StringBuilder(v[1]).append(StringConstants.SPACE).append(v[0]).toString();
						//into field 2, put Bezeichnung3=Zusatz
						adrs[i][2] = v[2];
						//into field 3, put Strasse
						adrs[i][3] = v[3];
						//into field 4, put PLZ SPACE Ort
						adrs[i][4] = new StringBuilder(v[4]).append(StringConstants.SPACE).append(v[5]).toString();
						//into field 5, put Telefon1
						adrs[i][5] = v[6];
						//into field 6, put Fax
						adrs[i][6] = v[7];
						//into field 7, put E-mail
						adrs[i][7] = v[8];
						 *
						 */
						
						/*
						 * 20210325js: Producing 1 Column where wrapping causes no harm
						 * (we could have made an interleaving product as well,
						 *  where every other row contains names / specialties or other data,
						 *  but I'm not that inclined to overzealous tabular alignment of the content)
						 */
						//i is the currently processed entry from the list of selected contacts
						//create an array of 4 strings for the currently processed entry
						//these will be output as 4 columns in the current row of the resulting table
						adrs[i] = new String[1];						
						
						//into field 0, put Title SPACE 
						//				Bezeichnung1 SPACE Bezeichnung2 SPACE Bezeichnung3
						//				,SPACE Bezeichnung3=Zusatz
						//				,SPACE put Strasse
						//				,SPACE put PLZ SPACE Ort
						//				,SPACE put Telefon1
						//				,SPACE put Fax
						//				,SPACE put E-mail
						//
						//And naturally this algorithm is not only unreadable
						//but also rather dumb - i.e. causing repeated commas
						//and spaces for empty fields.
						
						
						/*
						 * 20210325js: Produce one line, comma separated fields
						 *
						//The professional title must be obtained in a slightly complicated way.
						StringBuffer a = new StringBuffer();
						if (k.istPerson()) {
							Person p = Person.load(k.getId());
							a.append(p.get(Person.TITLE));
							if (a.length()>0) { a.append(StringConstants.SPACE); }
							};
						if (v[1].length()>0); { a.append(StringConstants.SPACE).append(v[1]); }			//First name
						if (v[0].length()>0); { a.append(StringConstants.SPACE).append(v[0]); }			//Family name
						if (v[2].length()>0); { a.append("," + StringConstants.SPACE).append(v[2]); }	//Specialty
						if (v[3].length()>0); { a.append("," + StringConstants.SPACE).append(v[3]); }	//Street
						if (v[4].length()+v[5].length()>0); { a.append(","); }							//ZIP + City
						if (v[4].length()>0); { a.append(StringConstants.SPACE).append(v[4]); }
						if (v[5].length()>0); { a.append(StringConstants.SPACE).append(v[5]); }
						if (v[6].length()>0); { a.append("," + StringConstants.SPACE).append(v[6]); }	//Phone
						if (v[7].length()>0); { a.append("," + StringConstants.SPACE) 
											           + "Fax" + StringConstants.SPACE).append(v[7]); }	//Fax
						if (v[8].length()>0); { a.append("," + StringConstants.SPACE).append(v[8]); }	//E-Mail
						 */

						/*
						 * 20210325js: Produce about four lines per contact -
						 * Title FirstName FamilyName
						 * Specialty
						 * Street No., ZIP City
						 * Phone, Fax, E-Mail
						 * New lines and Commas/Spaces are only synthesized when needed.
						 * The decisions are made in a fairly robust, but not yet perfect way.
						 * 
						 * TODO: This is not as well made as the routine for ...copySel... below,
						 * e.g. there is not cleaning of the title yet etc., but it already is
						 * rather robust against empty fields, and it returns a more beautiful
						 * output than the One-line/Four-fields-in-a-row/MuchInfoMissing-Format
						 * originally offered here by Elexis. 
						 * 
						 * And still, it's rather unreadable code - 
						 * please compare this with the longer but much more readily understandable
						 * code below and ask where you would rather be able to make a change
						 * and also be sure tha you can predict the result :-)
						 * 
						 * And this even though I've already added an info to the right of
						 * several lines showing which content is added where - if this weren't
						 * here, you'd rather not have a clue what v[4] might possibly mean etc.,
						 * or would you? - So please let these comments stay where they are,
						 * and don't even ruin their usability by applying short line breaks
						 * or putting them up or down to another line etc.
						 */
						//The professional title must be obtained in a slightly complicated way.
						StringBuffer a = new StringBuffer();
						if (k.istPerson()) {
							Person p = Person.load(k.getId());
							a.append(p.get(Person.TITLE));		//Don't automatically add a space here
							};
						if ( (    a.length()>0 ) && ( v[1].length() + v[0].length() )>0 ) { a.append(StringConstants.SPACE); }
						if (   v[1].length()>0 ) { a.append(v[1]); }											//First name
						if ( ( v[1].length()>0 ) && ( v[1].length()>0 ) ) { a.append(StringConstants.SPACE); }
						if (   v[0].length()>0 ) { a.append(v[0]); }											//Family name
						if (   v[2].length()>0 ) { a.append("\n").append(v[2]); }								//Specialty
						if ( ( v[3].length() + v[4].length() + v[5].length() ) >0 ) { a.append("\n"); }
						if (   v[3].length()>0 ) { a.append(v[3]); }											//Street
						if ( ( v[3].length()>0 ) && ( v[4].length()+v[5].length()>0 ) ) { a.append(","); }		//ZIP + City
						if (   v[4].length()>0 ) { a.append(StringConstants.SPACE).append(v[4]); }
						if (   v[5].length()>0 ) { a.append(StringConstants.SPACE).append(v[5]); }
						if ( ( v[6].length() + v[7].length() + v[8].length() ) >0 ) { a.append("\n"); }
						if (   v[6].length()>0 ) { a.append(v[6]); }											//Phone
						if ( ( v[6].length()>0 ) && (v[7].length()>0 ) ) { a.append("," + StringConstants.SPACE); }
						if (   v[7].length()>0 ) { a.append("Fax" + StringConstants.SPACE).append(v[7]); }		//Fax
						if ( ( ( v[6].length() + v[7].length() ) >0 ) && ( v[8].length()>0 ) ) { a.append("," + StringConstants.SPACE); }
						if (   v[8].length()>0 ) { a.append(v[8]); }											//E-Mail
						//If another address follows, add an emtpy line before that - 
						//otherwise the output of this would not be easily legible.
						if ( (    a.length()>0 ) && ( i < sel.length ) ) { a.append("\n"); }  
							
						adrs[i][0] = new StringBuilder(a).toString();
					}
					gpl.insertTable("[Liste]", adrs, null);
					gpl.open();
				}
			}
		};

		/*
		 * TODO: Refactor this procedure into different class(es)
		 * TODO: Should each field be capable of cleaning its content ? (Jörg Sigle & Niklaus Giger)
		 * TODO: We must find a way to handle different languages + research actual content of database columns
		 * TODO: Configuratability following preferences of diffferent users
		 * TODO: Maybe separate cleaning rules (into file) from cleaning code (in here, or in separate .java file(s))
		 *   
		 * @remark please note if at least one field of a contact is changed, all fields of the contact will be appended
		 *         to the clipboard. The result can be pasted into a spreadshead, and a macro exists to highlight then changed fields
		 *         This allows checking whether your algorithm is good or not 
		 * 
		 * 201303041746js:
		 * Clean selected address(es):
		 * For all selected addresses do:
		 * If FLD_IS_PATIENT==true, then set FLD_IS_PERSON=true (otherwise, invalid xml invoices may be produced, addressed to institutions instead of persons)
		 * For each address field: remove leading and trailing spaces. 
		 */
					
		tidySelectedAddressesAction =
				new Action(Messages.KontakteView_tidySelectedAddresses) { //$NON-NLS-1$
			{
				setImageDescriptor(Images.IMG_WIZARD.getImageDescriptor());
				setToolTipText(Messages.KontakteView_tidySelectedAddressesLong); //$NON-NLS-1$
			}
			
			@Override
			public void run(){
				
				//Adopted from KontakteView.printList:			
				//Convert the selected addresses into a list
				System.out.println("KontakteView tidySelectedAddressesAction.run begin");
				
				Object[] sel = cv.getSelection();
								
				if (sel != null && sel.length > 0) {
					//selectedAddressesText.setLength(0);
					//selectedAddressesText.append("jsdebug: Your selection includes "+sel.length+" element(s):"+System.getProperty("line.separator"));
					
					//Buffer for a list of all changed addresses (see below for the ratio behind that)
					StringBuffer SelectedContactInfosChangedList = new StringBuffer();
					
					
					System.out.println("KontakteView tidySelectedAddressesAction.run Processing "+sel.length+" entries...");
					for (int i = 0; i < sel.length; i++) {

						if (i % 100 == 0) {
							System.out.println("KontakteView tidySelectedAddressesAction.run Processing entry "+i+"...");		
						};
						
						//To check whether any changes were made,
						//synthesize a string with all affected address fields before and after the processing,
						//and compare them. That's probably faster to do - and not much more of a processing effort,
						//but much less programming hazzle - than monitoring the individual steps.
						StringBuffer SelectedContactInfosTextBefore = new StringBuffer();
						StringBuffer SelectedContactInfosTextAfter = new StringBuffer();	
						
						Kontakt k = (Kontakt) sel[i];

						/*
						 * Tidy all fields of the address
						 */
						
						//Maybe we should also tidy the PostAnschrift?
						//But that would probably be too complicated -
						//step 1: check whether it's made from the other available fields ONLY,
						//        if the slightest suspicion exists that user mods are included - don't touch,
						//        or get very intelligent first.
						//System.out.println(k.getPostAnschrift(true));
						//System.out.println(k.getPostAnschriftPhoneFaxEmail(true,true));
						
						//System.out.println(k.get(Kontakt.FLD_IS_PERSON));
						//System.out.println(k.get(Kontakt.FLD_IS_PATIENT));
						
						if (k.istPatient() && !k.istPerson()) {				
							System.out.println("KontakteView tidySelectedAddressesAction: corrected: FLD_IS_PATIENT w/o FLD_IS_PERSON: FLD_IS_PERSON set to StringContstants.ONE");
							k.set(Kontakt.FLD_IS_PERSON,StringConstants.ONE);						
						};
						
						//The following field identifiers are derived from Kontakt.java
						//Is there any way to evaluate all field definitions that exist over there,
						//and program a loop that would process all fields automatically?

						//Copy the complete content of k before the processing into a single string
						//so we can easily see later on if any changes were applied that would warrant a manual review of the postaddresse content.
						//Please note: if FLD_IS_PERSON, additional content will be added below.
						
						//The fields are resorted so that a relatively fast review of exported before/after sets is possible.
						//We want to get output even for empty fields; so no check for isNothing. The .append() will work w/o error even for empty fields (tested).
						//We want to be able to compare changed field lengths due to trailing spaces visually. Thus, addition of brackets arround each field.

						//if (!StringTool.isNothing(k.get(Kontakt.FLD_IS_LAB))) {SelectedContactInfosTextBefore.append("["+k.get(Kontakt.FLD_IS_LAB)+"]\t");};
						//if (!StringTool.isNothing(k.get(Kontakt.FLD_IS_MANDATOR))) {SelectedContactInfosTextBefore.append("["+k.get(Kontakt.FLD_IS_MANDATOR)+"]\t");};
						//if (!StringTool.isNothing(k.get(Kontakt.FLD_IS_USER))) {SelectedContactInfosTextBefore.append("["+k.get(Kontakt.FLD_IS_USER)+"]\t");};
						//if (!StringTool.isNothing(k.get(Kontakt.FLD_IS_ORGANIZATION))) {SelectedContactInfosTextBefore.append("["+k.get(Kontakt.FLD_IS_ORGANIZATION)+"]\t");};
						//if (!StringTool.isNothing(k.get(Kontakt.FLD_IS_PATIENT))) {SelectedContactInfosTextBefore.append("["+k.get(Kontakt.FLD_IS_PATIENT)+"]\t");};
						//if (!StringTool.isNothing(k.get(Kontakt.FLD_IS_PERSON))) {SelectedContactInfosTextBefore.append("["+k.get(Kontakt.FLD_IS_PERSON)+"]\t");};

						//Necessary (looking at the data) probably only for the Postadresse FLD_ANSCHRIFT field and the Bemerkung FLD_REMARK field,
						//but used as a precaution for all fields,
						//we want to replace newlines and carriage returns by |,
						//so that different numbers of lines per field in the Before and After field collections
						//would neither introduce vertical nor horizontal jitter in the exported fields table.
						//Otherwise, they would become very irregular and differences much more difficult to spot manually or by means of formulas within a spreasheet.
						
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_SHORT_LABEL))) */ {SelectedContactInfosTextBefore.append("["+k.get(Kontakt.FLD_SHORT_LABEL).replace("\n","|").replace("\r","|")+"]\t");};
						
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_NAME1))) */ {SelectedContactInfosTextBefore.append("["+k.get(Kontakt.FLD_NAME1).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_NAME2))) */ {SelectedContactInfosTextBefore.append("["+k.get(Kontakt.FLD_NAME2).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_NAME3))) */ {SelectedContactInfosTextBefore.append("["+k.get(Kontakt.FLD_NAME3).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_REMARK))) */ {SelectedContactInfosTextBefore.append("["+k.get(Kontakt.FLD_REMARK).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_PHONE1))) */ {SelectedContactInfosTextBefore.append("["+k.get(Kontakt.FLD_PHONE1).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_PHONE2))) */ {SelectedContactInfosTextBefore.append("["+k.get(Kontakt.FLD_PHONE2).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_STREET))) */ {SelectedContactInfosTextBefore.append("["+k.get(Kontakt.FLD_STREET).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_ZIP))) */ {SelectedContactInfosTextBefore.append("["+k.get(Kontakt.FLD_ZIP).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_PLACE))) */ {SelectedContactInfosTextBefore.append("["+k.get(Kontakt.FLD_PLACE).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_COUNTRY))) */ {SelectedContactInfosTextBefore.append("["+k.get(Kontakt.FLD_COUNTRY).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_E_MAIL))) */ {SelectedContactInfosTextBefore.append("["+k.get(Kontakt.FLD_E_MAIL).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_WEBSITE))) */ {SelectedContactInfosTextBefore.append("["+k.get(Kontakt.FLD_WEBSITE).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_MOBILEPHONE))) */ {SelectedContactInfosTextBefore.append("["+k.get(Kontakt.FLD_MOBILEPHONE).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_FAX))) */ {SelectedContactInfosTextBefore.append("["+k.get(Kontakt.FLD_FAX).replace("\n","|").replace("\r","|")+"]\t");};
						
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_ANSCHRIFT))) */ {SelectedContactInfosTextBefore.append("["+k.get(Kontakt.FLD_ANSCHRIFT).replace("\n","|").replace("\r","|")+"]\t");};
						
						
						
						//The same processing as for Person.TITLE (see below) is applied to the Kontakt.FLD_ANSCHRIFT field
						//no matter whether we deal with a person or an organization or whatever.
						//This field contains the Postanschrift,
						//where we may also assume that titles may occur (even for an organization, e.g. "Dr. Müller Pharma"),
						//and can be processed rather safely. 
						//This must be done BEFORE the trim() and replace() processing to remove excess spaces.
						//Enforce certain uppercase/lowercase conventions
						
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("prof.","Prof."));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("dr.","Dr."));
						//The following ones can not be used in the ANSCHRIFT field, because they would e.g.
						//ruin a "Facharzt Innere Med." to "Facharzt Innere med."
						/*
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("Med.","med."));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("Jur.","jur."));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("Rer.","rer."));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("Nat.","nat."));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("H.c.","h.c."));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("H.C.","h.c."));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("h.C.","h.c."));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("H. c.","h.c."));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("H. C.","h.c."));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("h. C.","h.c."));
						 */
						
						//We use these ones instead (and I am too tired to make up better regexes):
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("Dr.Med.","Dr.med."));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("Dr.Jur.","Dr.jur."));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("Dr.Rer.","Dr.rer."));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("Dr.Nat.","Dr.nat."));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("Dr. Med.","Dr. med."));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("Dr. Jur.","Dr. jur."));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("Dr. Rer.","Dr. rer."));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("Dr. Nat.","Dr. nat."));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("Innere med.","Innere Med."));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("Anthroposoph. med.","Anthroposoph. Med."));
						

						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("Prof.","Prof. "));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("Dr.","Dr. "));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("med.","med. "));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("jur.","jur. "));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("rer.","rer. "));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("nat.","nat. "));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("h.c.","h.c. "));
						//remove spaces within "h. c." to "h.c."
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("h. c.","h.c."));
						
						//The same processing as for Kontakt.FLD_NAME3 is applied to the Kontakt.FLD_ANSCHRIFT field.
						//Replace Facharzt f. xyz or Facharzt FMH f. xyz by Facharzt für xyz or Facharzt FMH für xyz
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("Facharzt f.","Facharzt für"));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("Facharzt FMH f.","Facharzt FMH für"));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("Fachärztin f.","Fachärztin für"));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).replace("Fachärztin FMH f.","Fachärztin FMH für"));		
					
						
						//replace three spaces in a row by one space
						//replace two spaces in a row by one space
						//remove leading and trailing spaces for each field that may contain free text input
						
						//Please note: replace 3->1 spaces must be placed left of replace 2->1 spaces, so that both can become active.
						//The processing functions are apparently executed from left to right.
						//If they were placed the other way round, and we have some "abc   xyz" string (with 3 consecutive spaces),
						//only two of these would be replaced by one, so that in the end, we get "abc  xyz", and the second replace would remain unused.
						//I've tried it out. And it makes a difference especially with respect to modified professional titles,
						//where existing single spaces are temporarily expanded and a cleanup is definitely needed in the same processing cycle.
						
						k.set(Kontakt.FLD_E_MAIL,k.get(Kontakt.FLD_E_MAIL).trim().replace("   "," ").replace("  "," "));
						k.set(Kontakt.FLD_WEBSITE,k.get(Kontakt.FLD_WEBSITE).trim().replace("   "," ").replace("  "," "));
						k.set(Kontakt.FLD_MOBILEPHONE,k.get(Kontakt.FLD_MOBILEPHONE).trim().replace("   "," ").replace("  "," "));
						k.set(Kontakt.FLD_FAX,k.get(Kontakt.FLD_FAX).trim().replace("   "," ").replace("  "," "));
						//k.set(Kontakt.FLD_IS_LAB,k.get(Kontakt.FLD_IS_LAB).trim().replace("   "," ").replace("  "," "));
						//k.set(Kontakt.FLD_IS_MANDATOR,k.get(Kontakt.FLD_IS_MANDATOR).trim().replace("   "," ").replace("  "," "));
						//k.set(Kontakt.FLD_IS_USER,k.get(Kontakt.FLD_IS_USER).trim().replace("   "," ").replace("  "," "));
						k.set(Kontakt.FLD_SHORT_LABEL,k.get(Kontakt.FLD_SHORT_LABEL).trim().replace("   "," ").replace("  "," "));
						//k.set(Kontakt.FLD_IS_ORGANIZATION,k.get(Kontakt.FLD_IS_ORGANIZATION).trim().replace("   "," ").replace("  "," "));
						//k.set(Kontakt.FLD_IS_PATIENT,k.get(Kontakt.FLD_IS_PATIENT).trim().replace("   "," ").replace("  "," "));
						//k.set(Kontakt.FLD_IS_PERSON,k.get(Kontakt.FLD_IS_PERSON).trim().replace("   "," ").replace("  "," "));
						k.set(Kontakt.FLD_ANSCHRIFT,k.get(Kontakt.FLD_ANSCHRIFT).trim().replace("   "," ").replace("  "," "));
						k.set(Kontakt.FLD_COUNTRY,k.get(Kontakt.FLD_COUNTRY).trim().replace("   "," ").replace("  "," "));
						k.set(Kontakt.FLD_PLACE,k.get(Kontakt.FLD_PLACE).trim().replace("   "," ").replace("  "," "));
						k.set(Kontakt.FLD_ZIP,k.get(Kontakt.FLD_ZIP).trim().replace("   "," ").replace("  "," "));
						k.set(Kontakt.FLD_STREET,k.get(Kontakt.FLD_STREET).trim().replace("   "," ").replace("  "," "));
						k.set(Kontakt.FLD_PHONE2,k.get(Kontakt.FLD_PHONE2).trim().replace("   "," ").replace("  "," "));
						k.set(Kontakt.FLD_PHONE1,k.get(Kontakt.FLD_PHONE1).trim().replace("   "," ").replace("  "," "));
						k.set(Kontakt.FLD_REMARK,k.get(Kontakt.FLD_REMARK).trim().replace("   "," ").replace("  "," "));
						k.set(Kontakt.FLD_NAME3,k.get(Kontakt.FLD_NAME3).trim().replace("   "," ").replace("  "," "));
						k.set(Kontakt.FLD_NAME2,k.get(Kontakt.FLD_NAME2).trim().replace("   "," ").replace("  "," "));
						k.set(Kontakt.FLD_NAME1,k.get(Kontakt.FLD_NAME1).trim().replace("   "," ").replace("  "," "));
										
						//Replace Facharzt f. xyz or Facharzt FMH f. xyz by Facharzt für xyz or Facharzt FMH für xyz
						k.set(Kontakt.FLD_NAME3,k.get(Kontakt.FLD_NAME3).replace("Facharzt f.","Facharzt für"));
						k.set(Kontakt.FLD_NAME3,k.get(Kontakt.FLD_NAME3).replace("Facharzt FMH f.","Facharzt FMH für"));
						k.set(Kontakt.FLD_NAME3,k.get(Kontakt.FLD_NAME3).replace("Fachärztin f.","Fachärztin für"));
						k.set(Kontakt.FLD_NAME3,k.get(Kontakt.FLD_NAME3).replace("Fachärztin FMH f.","Fachärztin FMH für"));		

						
						if (k.istPerson()) {
							Person p = Person.load(k.getId());

							//Please note that k.set(,k.get()) works in this section with Person.TITLE etc.
							//But p.set(,p.get)) does NOT work.
							//Gerry's comment says: A person is a contact with additional fields,
							//so well, it might be correct that the person is accessed via k...
							//and p.get() throws a no such method error.
							
							//Copy additional content of k (for KONTAKT - PERSON) before the processing into a single string
							//so we can easily see later on if any changes were applied that would warrant a manual review of the postaddresse content.
							//Please note: The fields of KONTAKT have already been added above.

							//The fields are resorted so that a relatively fast review of exported before/after sets is possible.
							//We want to get output even for empty fields; so no check for isNothing. The .append() will work w/o error even for empty fields (tested).
							//We want to be able to compare changed field lengths due to trailing spaces visually. Thus, addition of brackets arround each field.

							/* if (!StringTool.isNothing(k.get(Person.TITLE))) */ {SelectedContactInfosTextBefore.append("["+k.get(Person.TITLE).replace("\n","|").replace("\r","|")+"]\t");};
							/* if (!StringTool.isNothing(k.get(Person.FIRSTNAME))) */ {SelectedContactInfosTextBefore.append("["+k.get(Person.FIRSTNAME).replace("\n","|").replace("\r","|")+"]\t");};
							/* if (!StringTool.isNothing(k.get(Person.NAME))) */ {SelectedContactInfosTextBefore.append("["+k.get(Person.NAME).replace("\n","|").replace("\r","|")+"]\t");};
							/* if (!StringTool.isNothing(k.get(Person.SEX))) */ {SelectedContactInfosTextBefore.append("["+k.get(Person.SEX).replace("\n","|").replace("\r","|")+"]\t");};
							/* if (!StringTool.isNothing(k.get(Person.BIRTHDATE))) */ {SelectedContactInfosTextBefore.append("["+k.get(Person.BIRTHDATE).replace("\n","|").replace("\r","|")+"]\t");};
							/* if (!StringTool.isNothing(k.get(Person.MOBILE))) */ {SelectedContactInfosTextBefore.append("["+k.get(Person.MOBILE).replace("\n","|").replace("\r","|")+"]\t");};

							//Normalize a title like "Dr.med.", "Dr.Med." etc. to "Dr. med."
							//Or "Prof.Dr.med." to "Prof. Dr. med.",
							//But "h.c." shall remain "h.c."
							
							//Enforce certain uppercase/lowercase conventions
							
							k.set(Person.TITLE,k.get(Person.TITLE).replace("prof.","Prof."));
							k.set(Person.TITLE,k.get(Person.TITLE).replace("dr.","Dr."));
							k.set(Person.TITLE,k.get(Person.TITLE).replace("Med.","med."));
							k.set(Person.TITLE,k.get(Person.TITLE).replace("Jur.","jur."));
							k.set(Person.TITLE,k.get(Person.TITLE).replace("Rer.","rer."));
							k.set(Person.TITLE,k.get(Person.TITLE).replace("Nat.","nat."));
							k.set(Person.TITLE,k.get(Person.TITLE).replace("H.c.","h.c."));
							k.set(Person.TITLE,k.get(Person.TITLE).replace("H.C.","h.c."));
							k.set(Person.TITLE,k.get(Person.TITLE).replace("h.C.","h.c."));
							k.set(Person.TITLE,k.get(Person.TITLE).replace("H. c.","h.c."));
							k.set(Person.TITLE,k.get(Person.TITLE).replace("H. C.","h.c."));
							k.set(Person.TITLE,k.get(Person.TITLE).replace("h. C.","h.c."));
														
							//Add a space after dots after Dr., Prof. and med.
							//Adding spaces generally after each dot might produce too many unwanted changes.
							//This will produce one trailing space, which will be removed in the next step.
							
							//Very funny. replaceAll searches for regexp, where "." matches any character,
							//but Eclipse thinks that in the first argument, "\." is an invalid special character...
							//And yes. Tested. replaceAll("Prof.","Prof. ")) would really change "Profxmed." to "Prof. med."
							//So I'll search for ASCII character \x2E (hex) instead. Nope: "Prof\x2E": Invalid escape sequence again.
							//I'll use replace() instead of replaceAll) - both will replace all occurences; only the latter will use regexp.
							//But so I can't construct something like "replaceAll("Prof.([:alnum])",... to insert a space only, where more text follows.
							k.set(Person.TITLE,k.get(Person.TITLE).replace("Prof.","Prof. "));
							k.set(Person.TITLE,k.get(Person.TITLE).replace("Dr.","Dr. "));
							k.set(Person.TITLE,k.get(Person.TITLE).replace("med.","med. "));
							k.set(Person.TITLE,k.get(Person.TITLE).replace("jur.","jur. "));
							k.set(Person.TITLE,k.get(Person.TITLE).replace("rer.","rer. "));
							k.set(Person.TITLE,k.get(Person.TITLE).replace("nat.","nat. "));
							k.set(Person.TITLE,k.get(Person.TITLE).replace("h.c.","h.c. "));
							//remove spaces within "h. c." to "h.c."
							k.set(Person.TITLE,k.get(Person.TITLE).replace("h. c.","h.c."));
							
							//The following field identifiers are derived from Person.java
							//Is there any way to evaluate all field definitions that exist over there,
							//and program a loop that would process all fields automatically?

							k.set(Person.TITLE,k.get(Person.TITLE).trim().replace("   "," ").replace("  "," "));
							k.set(Person.MOBILE,k.get(Person.MOBILE).trim().replace("   "," ").replace("  "," "));
							k.set(Person.SEX,k.get(Person.SEX).trim().replace("   "," ").replace("  "," "));
							k.set(Person.BIRTHDATE,k.get(Person.BIRTHDATE).trim().replace("   "," ").replace("  "," "));
							k.set(Person.FIRSTNAME,k.get(Person.FIRSTNAME).trim().replace("   "," ").replace("  "," "));
							k.set(Person.NAME,k.get(Person.NAME).trim().replace("   "," ").replace("  "," "));
							//k.set(Person.MALE,k.get(Person.MALE).trim().replace("   "," ").replace("  "," "));
							//k.set(Person.FEMALE,k.get(Person.FEMALE).trim().replace("   "," ").replace("  "," "));

							//Now, let's replace "xyz FMH" by "Facharzt/Fachärztin für xyz FMH",
							//like in: Ron Ammann: "Dermatologie/Venerologie FMH"
							//We need to take the sex into account (if known), 
							//that's why we do this down here in the "person" related section.
						
							//Ich schalte das mal aus - weil es zusätzlich Sprachunterstützung bräuchte.
							//Also, die Strings müssten ggf. aus messages.properties geholt werden,
							//und wegen der Umlaute dann auch noch in mehreren Varianten vorliegen oder regexp verwenden.
							//Facharzt für... = spécialiste en ...; und in italienisch will ich es gerade nicht nachschauen.
							//If this should be activated, you may also want to implement the same processing to Kontakt.FLD_ANSCHRIFT.
							//See further above for explanations.
							if (false) {
								if ( k.get(Person.SEX).equals(Person.MALE)
										&& (k.get(Kontakt.FLD_NAME3).toUpperCase().indexOf("FACHARZT") < 0)
										&& (k.get(Kontakt.FLD_NAME3).toUpperCase().indexOf("FMH") >=0) ) {
										k.set(Kontakt.FLD_NAME3,k.get(Kontakt.FLD_NAME3).replaceAll("(.*) FMH$","Facharzt für $1 FMH"));		
								} else if ( k.get(Person.SEX).equals(Person.FEMALE)
										&& (k.get(Kontakt.FLD_NAME3).toUpperCase().indexOf("FACHÄRZT") < 0)
										&& (k.get(Kontakt.FLD_NAME3).toUpperCase().indexOf("FACHAERZT") < 0)
										&& (k.get(Kontakt.FLD_NAME3).toUpperCase().indexOf("FMH") >=0) ) {
										k.set(Kontakt.FLD_NAME3,k.get(Kontakt.FLD_NAME3).replaceAll("(.*) FMH$","Fachärztin für $1 FMH"));		
								}
							}  //js: if (false)
						
						}


						

						//Copy the complete content of k before the processing into a single string
						//so we can easily see later on if any changes were applied that would warrant a manual review of the postaddresse content.
						//Please note: if FLD_IS_PERSON, additional content will be added below.
						
						//The fields are resorted so that a relatively fast review of exported before/after sets is possible.
					
						//if (!StringTool.isNothing(k.get(Kontakt.FLD_IS_LAB))) {SelectedContactInfosTextAfter.append(k.get(Kontakt.FLD_IS_LAB)+"\t");};
						//if (!StringTool.isNothing(k.get(Kontakt.FLD_IS_MANDATOR))) {SelectedContactInfosTextAfter.append(k.get(Kontakt.FLD_IS_MANDATOR)+"\t");};
						//if (!StringTool.isNothing(k.get(Kontakt.FLD_IS_USER))) {SelectedContactInfosTextAfter.append(k.get(Kontakt.FLD_IS_USER)+"\t");};
						//if (!StringTool.isNothing(k.get(Kontakt.FLD_IS_ORGANIZATION))) {SelectedContactInfosTextAfter.append(k.get(Kontakt.FLD_IS_ORGANIZATION)+"\t");};
						//if (!StringTool.isNothing(k.get(Kontakt.FLD_IS_PATIENT))) {SelectedContactInfosTextAfter.append(k.get(Kontakt.FLD_IS_PATIENT)+"\t");};
						//if (!StringTool.isNothing(k.get(Kontakt.FLD_IS_PERSON))) {SelectedContactInfosTextAfter.append(k.get(Kontakt.FLD_IS_PERSON)+"\t");};
						
						//Necessary (looking at the data) probably only for the Postadresse FLD_ANSCHRIFT field and the Bemerkung FLD_REMARK field,
						//but used as a precaution for all fields,
						//we want to replace newlines and carriage returns by |,
						//so that different numbers of lines per field in the Before and After field collections
						//would neither introduce vertical nor horizontal jitter in the exported fields table.
						//Otherwise, they would become very irregular and differences much more difficult to spot manually or by means of formulas within a spreasheet.

						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_SHORT_LABEL))) */ {SelectedContactInfosTextAfter.append("["+k.get(Kontakt.FLD_SHORT_LABEL).replace("\n","|").replace("\r","|")+"]\t");};

						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_NAME1))) */ {SelectedContactInfosTextAfter.append("["+k.get(Kontakt.FLD_NAME1).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_NAME2))) */ {SelectedContactInfosTextAfter.append("["+k.get(Kontakt.FLD_NAME2).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_NAME3))) */ {SelectedContactInfosTextAfter.append("["+k.get(Kontakt.FLD_NAME3).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_REMARK))) */ {SelectedContactInfosTextAfter.append("["+k.get(Kontakt.FLD_REMARK).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_PHONE1))) */ {SelectedContactInfosTextAfter.append("["+k.get(Kontakt.FLD_PHONE1).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_PHONE2))) */ {SelectedContactInfosTextAfter.append("["+k.get(Kontakt.FLD_PHONE2).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_STREET))) */ {SelectedContactInfosTextAfter.append("["+k.get(Kontakt.FLD_STREET).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_ZIP))) */ {SelectedContactInfosTextAfter.append("["+k.get(Kontakt.FLD_ZIP).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_PLACE))) */ {SelectedContactInfosTextAfter.append("["+k.get(Kontakt.FLD_PLACE).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_COUNTRY))) */ {SelectedContactInfosTextAfter.append("["+k.get(Kontakt.FLD_COUNTRY).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_E_MAIL))) */ {SelectedContactInfosTextAfter.append("["+k.get(Kontakt.FLD_E_MAIL).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_WEBSITE))) */ {SelectedContactInfosTextAfter.append("["+k.get(Kontakt.FLD_WEBSITE).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_MOBILEPHONE))) */ {SelectedContactInfosTextAfter.append("["+k.get(Kontakt.FLD_MOBILEPHONE).replace("\n","|").replace("\r","|")+"]\t");};
						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_FAX))) */ {SelectedContactInfosTextAfter.append("["+k.get(Kontakt.FLD_FAX).replace("\n","|").replace("\r","|")+"]\t");};

						/* if (!StringTool.isNothing(k.get(Kontakt.FLD_ANSCHRIFT))) */ {SelectedContactInfosTextAfter.append("["+k.get(Kontakt.FLD_ANSCHRIFT).replace("\n","|").replace("\r","|")+"]\t");};

						//Copy additional content of k (for KONTAKT - PERSON) after the processing into a single string
						//so we can easily see later on if any changes were applied that would warrant a manual review of the postaddresse content.
						//Please note: The fields of KONTAKT have already been added above.

						if (k.istPerson()) {
							//Person p = Person.load(k.getId()); //This has been done above. Will the results of this action persist outside that block? Hopefully.

							//The fields are resorted so that a relatively fast review of exported before/after sets is possible.

							/* if (!StringTool.isNothing(k.get(Person.TITLE))) */ {SelectedContactInfosTextAfter.append("["+k.get(Person.TITLE).replace("\n","|").replace("\r","|")+"]\t");};
							/* if (!StringTool.isNothing(k.get(Person.FIRSTNAME))) */ {SelectedContactInfosTextAfter.append("["+k.get(Person.FIRSTNAME).replace("\n","|").replace("\r","|")+"]\t");};
							/* if (!StringTool.isNothing(k.get(Person.NAME))) */ {SelectedContactInfosTextAfter.append("["+k.get(Person.NAME).replace("\n","|").replace("\r","|")+"]\t");};
							/* if (!StringTool.isNothing(k.get(Person.SEX))) */ {SelectedContactInfosTextAfter.append("["+k.get(Person.SEX).replace("\n","|").replace("\r","|")+"]\t");};
							/* if (!StringTool.isNothing(k.get(Person.BIRTHDATE))) */ {SelectedContactInfosTextAfter.append("["+k.get(Person.BIRTHDATE).replace("\n","|").replace("\r","|")+"]\t");};
							/* if (!StringTool.isNothing(k.get(Person.MOBILE))) */ {SelectedContactInfosTextAfter.append("["+k.get(Person.MOBILE).replace("\n","|").replace("\r","|")+"]\t");};
						}

						//If anything has changed, then add the current address to the list of changed addresses.
						//Actually, I'm adding both the content before and after the processing; without any trailing tab.
						//We will output that list later on - to the clipboard - so that the Postadresse for any changed addresses can be reviewed.
						if ( !SelectedContactInfosTextAfter.toString().equals(SelectedContactInfosTextBefore.toString()) ) {
							System.out.println("Before: ["+SelectedContactInfosTextBefore+"]");
							System.out.println("After:  ["+SelectedContactInfosTextAfter+"]");
							SelectedContactInfosTextBefore.delete(SelectedContactInfosTextBefore.length(),SelectedContactInfosTextBefore.length());
							SelectedContactInfosTextAfter.delete(SelectedContactInfosTextAfter.length(),SelectedContactInfosTextAfter.length());
							SelectedContactInfosChangedList.append("Before:\t"+SelectedContactInfosTextBefore+"\n");
							SelectedContactInfosChangedList.append("After:\t"+SelectedContactInfosTextAfter+"\n");
						}
						
					//I DON'T know whether this is required - most probably not,
					//I guess that something equivalent is included in the implementation of k.set();
					//anyway - it will NOT cause any window display contents to be updated at all.
					//Inform the system that the given persistent element has changed
					//ElexisEventDispatcher.update(k);
						
					}		//js: for each element in sel do

				
				
					/*
					 * 201303050117js:
					 * In order to export the list of addresses that might warrant a manual review of Postadresse to the clipboard,
					 * I have added the clipboard export routine also used in the copyToClipboard... methods further below.
					 * If not for this purpose, building up the stringsBuffer content would not have been required,
					 * and neither would have been any kind of clipboard interaction.
					 */
					/*
					 * 20120130js:
					 * I would prefer to move the following code portions down behind the "if sel not empty" block,
					 * so that (a) debugging output can be produced and (b) the clipboard will be emptied
					 * when NO Contacts have been selected. I did this to avoid the case where a user would assume
					 * they had selected some address, copied data to the clipboard, and pasted them - and, even
					 * when they erred about their selection, which was indeed empty, they would not immediately
					 * notice that because some (old, unchanged) content would still come out of the clipboard.
					 * 
					 * But if I do so, and there actually is no address selected, I get an error window:
					 * Unhandled Exception ... not valid. So to avoid that message without any further research
					 * (I need to get this work fast now), I move the code back up and leave the clipboard
					 * unchanged for now, if no Contacts had been selected to process.
					 * 
					 * (However, I may disable the toolbar icon / menu entry for this action in that case later on.) 
				 	 */				 	 

					//201305260049js
					// TO DO maybe this could also be refactored together with copySelected... content generators.
					// BUT CAVE: This procedure here should return content unchanged. No postprocessing please!
					
				    //System.out.print("jsdebug: SelectedContactInfosText: \n"+SelectedContactInfosText+"\n");
					
					//Adopted from BestellView.exportClipboardAction:
					//Copy some generated object.toString() to the clipoard
					
					if ( (SelectedContactInfosChangedList != null) && (SelectedContactInfosChangedList.length()>0) ) {
					
						Clipboard clipboard = new Clipboard(UiDesk.getDisplay());
						TextTransfer textTransfer = TextTransfer.getInstance();
						Transfer[] transfers = new Transfer[] {	textTransfer };
						Object[] data = new Object[] {
							SelectedContactInfosChangedList.toString()
						};
						clipboard.setContents(data, transfers);
						clipboard.dispose();
					}

				}			//js: if sel not empty
			
			//update display to reflect changed information
				
			//This updates the "Kontakte" window, but not the "Kontakt Detail" window:
			System.out.println("KontakteView tidySelectedAddressesAction.run Triggering update of the 'Kontakte' Window.");
			System.out.println("KontakteView tidySelectedAddressesAction.run This will lose the selection, but sadly NOT update 'Kontakt Detail'.");			
			cv.getConfigurer().getControlFieldProvider().fireChangedEvent();

			// TODO: Wenn nur ein einziger Kontakt markiert und geputzt wurde, sollte die Anzeige in KontaktDetail
			//       anschliessend sofort aktualisiert werden, damit der Nutzer den neuen Content sieht.
			
			//Wenn mehrere Einträge aktualisiert waren, und nach obigem die Selektion verschwindet,
			//wird inhärent auch der Inhalt von Kontakt Detail geändert (weil wahrscheinlich ein anderer Eintrag selektiert wird),
			//und somit auch dieses aktualisiert. Bei nur einem a priori selektierten und per tidy... behandelten Eintrag
			//erfolgt das Update aber erst, wenn man einen anderen und dann wieder diesen Patienten selektiert. :-(
			
			//Nachfolgend noch diverse Versuche, das zu erreichen - aber da sitz ich schon wieder Stunden,
			//möglicherweise bräuchte KontaktDetailView oder KontaktBlatt dafür eine Methode,
			//und/oder man muss irgendwas mit parent.layout aufrufen etc. Das ist jetzt NICHT vordringlich - keine Zeit dafür.
					

			/*
			 * Was folgt, liefert einige Informationen - offenbar ist Kontakt Details ein scrolled form,
			 * mit zwei Teilen - aber das hilft mir alles nichts. Ich kann es nicht einfach redrawen. 
			try {
				KontaktDetailView kdv =
					(KontaktDetailView) getSite().getPage().showView(KontaktDetailView.ID);
				
				//kdv.getClass().
				
				//das reicht alles nicht, auch nicht zusammen - es betrifft wohl eher das Layout als den Inhalt:
				System.out.println(kdv.getTitle());
				
				kdv.kb.changed(kdv.kb.getChildren());
				
				Object[] kbvch = kdv.kb.getChildren();
				if (kbvch != null && kbvch.length>0) {
					for (int i=0;i<kbvch.length;i++) {
						//This returns only one child:
						//kbvch[0] = ScrolledForm {}; class org.eclipse.ui.forms.widgets.ScrolledForm
						System.out.println("kbvch["+i+"] = "+kbvch[i].toString()+"; "+kbvch[i].getClass().toString());
						
						Object[] kbvchscrolledformfields = kbvch[i].getClass().getFields();

						if (kbvchscrolledformfields != null && kbvchscrolledformfields.length>0) {
							for (int j=0;j<kbvchscrolledformfields.length;j++) {
								//This returns two children:
								//kbvchscrolledformfields[0] = public long org.eclipse.swt.widgets.Composite.embeddedHandle; class java.lang.reflect.Field
								//kbvchscrolledformfields[1] = public long org.eclipse.swt.widgets.Widget.handle; class java.lang.reflect.Field
							
								System.out.println("kbvchscrolledformfields["+j+"] = "+kbvchscrolledformfields[j].toString()+"; "+kbvchscrolledformfields[j].getClass().toString());
								
								Object[] kbvchlevel3 = kbvchscrolledformfields[j].getClass().getFields();

								if (kbvchlevel3 != null && kbvchlevel3.length>0) {
									for (int k=0;k<kbvchlevel3.length;k++) {
										//This returns ... children:
										//kbvchscrolledformfields[0] = public long org.eclipse.swt.widgets.Composite.embeddedHandle; class java.lang.reflect.Field
										//kbvchlevel3[0] = public static final int java.lang.reflect.Member.PUBLIC; class java.lang.reflect.Field
										//kbvchlevel3[1] = public static final int java.lang.reflect.Member.DECLARED; class java.lang.reflect.Field
										//kbvchscrolledformfields[1] = public long org.eclipse.swt.widgets.Widget.handle; class java.lang.reflect.Field
										//kbvchlevel3[0] = public static final int java.lang.reflect.Member.PUBLIC; class java.lang.reflect.Field
										//kbvchlevel3[1] = public static final int java.lang.reflect.Member.DECLARED; class java.lang.reflect.Field
									
										System.out.println("kbvchlevel3["+k+"] = "+kbvchlevel3[k].toString()+"; "+kbvchlevel3[k].getClass().toString());
								
									}
								} //js: for k
								
								
							}
						} //js: for j
			
					}
				} //js: for i
				
				kdv.kb.redraw();
				kdv.kb.update();
				kdv.kb.layout(true);
				
				//kdv.kb.catchElexisEvent(new ElexisEvent(nullKontakt.class,ElexisEvent.EVENT_UPDATE));
		
				//kdv.kb.catchElexisEvent(new ElexisEvent(obj, obj.getClass(),
				//	ElexisEvent.EVENT_SELECTED));
			} catch (PartInitException e) {
				ExHandler.handle(e);
			}
			*/
			
			
			//Inform the system that all objects of a given class have to be loaded from storage.
			//Sorry, but it will NOT cause any window content to be updated.
			//ElexisEventDispatcher.reload(Kontakt.class);
			
			System.out.println("KontakteView tidySelectedAddressesAction.run TODO: Update display w/o losing selection, including Kontakt Details, even for only 1 entry processed.");			
			System.out.println("KontakteView tidySelectedAddressesAction.run TODO: Maybe add a progress bar.");			
			
			System.out.println("KontakteView tidySelectedAddressesAction.run end");
			};  	//js: copySelectedAddressesToClipboardAction.run()
		};		//js: tidySelectedAddressesAction = new Action()
	
			
		/* 201303141833js same or similar code is now also included in Patientenblatt2.java
		 * 201202161220js:
		 * Copy selected contact data (complete) to the clipboard, so it/they can be easily pasted into a target document
		 * for various further usage. This variant produces a more complete data set than copySelectedAddresses... below;
		 * it also includes the phone numbers and does not use the postal address, but all the individual data fields.
		 * Two actions with identical / similar code has also been added to PatientenListeView.java 
		 */
		copySelectedContactInfosToClipboardAction =
			new Action(Messages.KontakteView_copySelectedContactInfosToClipboard) { // $NON-NLS-1$
			{
				setImageDescriptor(Images.IMG_CLIPBOARD.getImageDescriptor());
				setToolTipText(Messages.KontakteView_copySelectedContactInfosToClipboard); // $NON-NLS-1$
			}
					
			@Override
			public void run(){
				
				//Adopted from KontakteView.printList:			
				//Convert the selected contacts into a list

				StringBuffer SelectedContactInfosText = new StringBuffer();
								
				Object[] sel = cv.getSelection();
				
				//js: If you enable the following line for debug output,
				//    you should also enable the SelectedContactInfosText.setLength(0) line below,
				//    and enable output of SelectedContactInfosText even for the case of an empty selection further below.
				//SelectedContactInfosText.append("jsdebug: Sorry, your selection is empty.");
				
				if (sel != null && sel.length > 0) {
					//SelectedContactInfosText.setLength(0);
					//SelectedContactInfosText.append("jsdebug: Your selection includes "+sel.length+" element(s):"+System.getProperty("line.separator"));
					
					for (int i = 0; i < sel.length; i++) {
						Kontakt k = (Kontakt) sel[i];
						
						//System.out.print("jsdebug: SelectedContactInfos.k.toString(): \n"+k.toString()+"\n");

						//TODO: The following code is adopted from Kontakt.createStdAnschrift for a different purpose/layout:
						//ggf. hier zu Person.getPersonalia() eine abgewandelte Fassung hinzufügen und von hier aus aufrufen.
						
						// Update+Info: 20210402js: Now I replaced getPersonlia() by a
						// a user configurable version, controlled by a template string
						// and two flags withAge and withKuerzel.
						
						//This highly similar (but still different) code has been adopted from my addition
						//to PatientenListeView.java CopySelectedPatInfosToClipboard... 201202161313js

						//optional code; this could be made configurable. For now: disabled by if (false)...
						if (false) {
							//I put the field of "Kürzel" in front. It contains a Patient ID number,
							//and optionally kk... for health insurances, or vn initials as Vorname Nachname for physicians. 
							String thisKontaktFLD_SHORT_LABEL = k.get(k.FLD_SHORT_LABEL); //$NON-NLS-1$
							if (!StringTool.isNothing(thisKontaktFLD_SHORT_LABEL)) {
								SelectedContactInfosText.append(thisKontaktFLD_SHORT_LABEL).append(",").append(StringTool.space);
							}
						}					
						
						if (k.istPerson()) {
							// Here, we need to look at the Person variant of a Kontakt to obtain their sex; 201202161326js
							// Kontakt cannot simply be cast to Person - if we try, we'll throw an error, and the remainder of this action will be ignored.
							// Person p = (Person) sel[i]; //THIS WILL NOT WORK.
							// So obtain the corresponding Person for a Kontakt via the ID:
							Person p = Person.load(k.getId());
									
							String salutation;
							// TODO default salutation might be configurable (or a "Sex missing!" Info might appear) js 
							if (p.getGeschlecht().equals(Person.MALE)) {							
								salutation = Messages.KontakteView_SalutationM ; //$NON-NLS-1$
							} else  //We do not use any default salutation for unknown sex to avoid errors!
							if (p.getGeschlecht().equals(Person.FEMALE)) {							
								salutation = Messages.KontakteView_SalutationF ; //$NON-NLS-1$
							} else { salutation = ""; //$NON-NLS-1$
							}
							
							if (!StringTool.isNothing(salutation)) {	//salutation should currently never be empty, but paranoia...
								SelectedContactInfosText.append(salutation);
								SelectedContactInfosText.append(StringTool.space);
							}
								
							String titel = p.get(p.TITLE); //$NON-NLS-1$
							if (!StringTool.isNothing(titel)) {
								SelectedContactInfosText.append(titel).append(StringTool.space);
							}
							//js: A comma between Family Name and Given Name would be generally helpful to reliably tell them apart:
							//SelectedContactInfosText.append(k.getName()+","+StringTool.space+k.getVorname());
							//js: But Jürg Hamacher prefers this in his letters without a comma in between:
							//SelectedContactInfosText.append(p.getName()+StringTool.space+p.getVorname());
							//Whereas I use the above variant for PatientenListeView.java;
							//I put the Vorname first in KontakteView. And I only use a spacer, if the first field is not empty!
							//SelectedContactInfosText.append(p.getVorname()+StringTool.space+p.getName());
							if (!StringTool.isNothing(p.getVorname())) {
								SelectedContactInfosText.append(p.getVorname()+StringTool.space);
							}
							if (!StringTool.isNothing(p.getName())) {
								SelectedContactInfosText.append(p.getName());
							}
							
							//Also, in KontakteView, I copy the content of fields "Bemerkung" and "Zusatz" as well.
							//"Zusatz" is mapped to "Bezeichnung3" in Person.java.
							String thisPersonFLD_REMARK = p.get(p.FLD_REMARK); //$NON-NLS-1$
							if (!StringTool.isNothing(thisPersonFLD_REMARK)) {
								SelectedContactInfosText.append(",").append(StringTool.space).append(thisPersonFLD_REMARK);
							}
							String thisPersonFLD_NAME3 = p.get(p.FLD_NAME3); //$NON-NLS-1$
							if (!StringTool.isNothing(thisPersonFLD_NAME3)) {
								SelectedContactInfosText.append(",").append(StringTool.space).append(thisPersonFLD_NAME3);
							}						

							String thisPatientBIRTHDATE = (String) p.get(p.BIRTHDATE);
							if (!StringTool.isNothing(thisPatientBIRTHDATE)) {
							//js: This would add the term "geb." (born on the) before the date of birth:
							//	SelectedContactInfosText.append(","+StringTool.space+"geb."+StringTool.space+new TimeTool(thisPatientBIRTHDATE).toString(TimeTool.DATE_GER));
							//js: But Jürg Hamacher prefers the patient information in his letters without that term:
							SelectedContactInfosText.append(","+StringTool.space+new TimeTool(thisPatientBIRTHDATE).toString(TimeTool.DATE_GER));
							}
						} else {	//if (k.istPerson())... else...
							String thisAddressFLD_NAME1 = (String) k.get(k.FLD_NAME1);
							String thisAddressFLD_NAME2 = (String) k.get(k.FLD_NAME2);
							String thisAddressFLD_NAME3 = (String) k.get(k.FLD_NAME3);
							if (!StringTool.isNothing(thisAddressFLD_NAME1)) {
								SelectedContactInfosText.append(thisAddressFLD_NAME1);
								if (!StringTool.isNothing(thisAddressFLD_NAME2+thisAddressFLD_NAME3)) {
									SelectedContactInfosText.append(StringTool.space);
								}
							}
							if (!StringTool.isNothing(thisAddressFLD_NAME2)) {
								SelectedContactInfosText.append(thisAddressFLD_NAME2);
							}
							if (!StringTool.isNothing(thisAddressFLD_NAME3)) {
								SelectedContactInfosText.append(thisAddressFLD_NAME3);
							}
							if (!StringTool.isNothing(thisAddressFLD_NAME3)) {
								SelectedContactInfosText.append(StringTool.space);
							}
						}

						String thisAddressFLD_STREET = (String) k.get(k.FLD_STREET);
						if (!StringTool.isNothing(thisAddressFLD_STREET)) {
							SelectedContactInfosText.append(","+StringTool.space+thisAddressFLD_STREET);
						}

						String thisAddressFLD_COUNTRY = (String) k.get(k.FLD_COUNTRY);
						if (!StringTool.isNothing(thisAddressFLD_COUNTRY)) {
							SelectedContactInfosText.append(","+StringTool.space+thisAddressFLD_COUNTRY+"-");
						}
							
						String thisAddressFLD_ZIP = (String) k.get(k.FLD_ZIP);
						if (!StringTool.isNothing(thisAddressFLD_ZIP)) {
								if (StringTool.isNothing(thisAddressFLD_COUNTRY)) {
										SelectedContactInfosText.append(","+StringTool.space);
									};
							SelectedContactInfosText.append(thisAddressFLD_ZIP);
						};
										
						String thisAddressFLD_PLACE = (String) k.get(k.FLD_PLACE);
						if (!StringTool.isNothing(thisAddressFLD_PLACE)) {
							if (StringTool.isNothing(thisAddressFLD_COUNTRY) && StringTool.isNothing(thisAddressFLD_ZIP)) {
								SelectedContactInfosText.append(",");
							};
							SelectedContactInfosText.append(StringTool.space+thisAddressFLD_PLACE);
						}

						String thisAddressFLD_PHONE1 = (String) k.get(k.FLD_PHONE1);
						if (!StringTool.isNothing(thisAddressFLD_PHONE1)) {
								SelectedContactInfosText.append(","+StringTool.space+StringTool.space+thisAddressFLD_PHONE1);
						}
							
						String thisAddressFLD_PHONE2 = (String) k.get(k.FLD_PHONE2);
						if (!StringTool.isNothing(thisAddressFLD_PHONE2)) {
							SelectedContactInfosText.append(","+StringTool.space+StringTool.space+thisAddressFLD_PHONE2);
						}
							
						String thisAddressFLD_MOBILEPHONE = (String) k.get(k.FLD_MOBILEPHONE);
						if (!StringTool.isNothing(thisAddressFLD_MOBILEPHONE)) {
							//With a colon after the label:
							//SelectedContactInfosText.append(","+StringTool.space+k.FLD_MOBILEPHONE+":"+StringTool.space+thisAddressFLD_MOBILEPHONE);
							//Without a colon after the label:
							//20160726070050js: THISWASACTIVE: SelectedContactInfosText.append(","+StringTool.space+k.FLD_MOBILEPHONE+StringTool.space+thisAddressFLD_MOBILEPHONE);
							//20160726070050js: Without the label NatelNr at all:
							SelectedContactInfosText.append(","+StringTool.space+thisAddressFLD_MOBILEPHONE);
						}
							
						String thisAddressFLD_FAX = (String) k.get(k.FLD_FAX);
						if (!StringTool.isNothing(thisAddressFLD_FAX)) {
							//With a colon after the label:
							//SelectedContactInfosText.append(","+StringTool.space+k.FLD_FAX+":"+StringTool.space+thisAddressFLD_FAX);
							//Without a colon after the label:
							SelectedContactInfosText.append(","+StringTool.space+k.FLD_FAX+StringTool.space+thisAddressFLD_FAX);
						}
							
						String thisAddressFLD_E_MAIL = (String) k.get(k.FLD_E_MAIL);
						if (!StringTool.isNothing(thisAddressFLD_E_MAIL)) {
							SelectedContactInfosText.append(","+StringTool.space+thisAddressFLD_E_MAIL);
						}							

						//Add another empty line (or rather: paragraph), if at least one more address will follow.
						if (i<sel.length-1) {
							SelectedContactInfosText.append(System.getProperty("line.separator"));
						}
						
					}		//js: for each element in sel do

					/*
					 * 20120130js:
					 * I would prefer to move the following code portions down behind the "if sel not empty" block,
					 * so that (a) debugging output can be produced and (b) the clipboard will be emptied
					 * when NO Contacts have been selected. I did this to avoid the case where a user would assume
					 * they had selected some address, copied data to the clipboard, and pasted them - and, even
					 * when they erred about their selection, which was indeed empty, they would not immediately
					 * notice that because some (old, unchanged) content would still come out of the clipboard.
					 * 
					 * But if I do so, and there actually is no address selected, I get an error window:
					 * Unhandled Exception ... not valid. So to avoid that message without any further research
					 * (I need to get this work fast now), I move the code back up and leave the clipboard
					 * unchanged for now, if no Contacts had been selected to process.
					 * 
					 * (However, I may disable the toolbar icon / menu entry for this action in that case later on.) 
				 	 */				 	 

					//201305260049js
					//For Patientenblatt2.java, PatientenListeView.java, KontakteView.java
					//This function was primarily introduced because the above algorithm
					//returns a double space in the phone number area. Maybe that from a field containing a space,
					//or truly generated in the code above.
					
					//Right now I have no time, and the postprocessing has the advantage of correcting unwanted
					//content inside the fields as well. So I only add this today.
					
					// TO DO Please look up the reason for the extra space above.
					// TO DO Please make sure that multibyte Unicode characters are correctly handled below. 
					// TO DO Add similar postprocessing to copySelectedAdressesToClipboard of all three .java files
					// TO DO Move the processing into a separate method/function, when the copy... methods are refactored.
						
					//Postprocess selectedContactInfosText:
					//Remove any leading or trailing spaces;
					//Replace any " ," by ",";
					//Replace any " ." by ".";
					//Replace any multiple spaces by single spaces.
					int n=0;
					while (n<SelectedContactInfosText.length()) {
						if (   SelectedContactInfosText.codePointAt(n)==StringTool.space.codePointAt(0)
							&& ( n==SelectedContactInfosText.length()
								|| SelectedContactInfosText.codePointAt(n+1)==(",").codePointAt(0)
								|| SelectedContactInfosText.codePointAt(n+1)==(".").codePointAt(0)
								|| SelectedContactInfosText.codePointAt(n+1)==StringTool.space.codePointAt(0) )
							){ 
							SelectedContactInfosText.deleteCharAt(n);
							} else { 
							n=n+1;}
					};				
					
				    //System.out.print("jsdebug: SelectedContactInfosText: \n"+SelectedContactInfosText+"\n");
					
					//Adopted from BestellView.exportClipboardAction:
					//Copy some generated object.toString() to the clipoard
					
					Clipboard clipboard = new Clipboard(UiDesk.getDisplay());
					TextTransfer textTransfer = TextTransfer.getInstance();
					Transfer[] transfers = new Transfer[] {	textTransfer };
					Object[] data = new Object[] {
						SelectedContactInfosText.toString()
					};
					clipboard.setContents(data, transfers);
					clipboard.dispose();
				}			//js: if sel not empty
			};  	//js: copySelectedContactInfosToClipboardAction.run()
		};

		/* 201303141833js same or similar code is now also included in Patientenblatt2.java
		 * 201201280147js:
		 * Copy selected address(es) to the clipboard, so it/they can be easily pasted into a letter for printing.
		 * Two actions with identical / similar code has also been added to PatientenListeView.java 
		 */
		copySelectedAddressesToClipboardAction = new Action(Messages.KontakteView_copySelectedAddressesToClipboard) { //$NON-NLS-1$
			{
				setImageDescriptor(Images.IMG_CLIPBOARD.getImageDescriptor());
				setToolTipText(Messages.KontakteView_copySelectedAddressesToClipboard); //$NON-NLS-1$
			}
			
			@Override
			public void run(){
				
				//Adopted from KontakteView.printList:			
				//Convert the selected addresses into a list

				StringBuffer selectedAddressesText = new StringBuffer();
								
				Object[] sel = cv.getSelection();
				
				//js: If you enable the following line for debug output,
				//    you should also enable the selectedAddressesText.setLength(0) line below,
				//    and enable output of selectedAddressesText even for the case of an empty selection further below.
				//selectedAddressesText.append("jsdebug: Sorry, your selection is empty.");
				
				if (sel != null && sel.length > 0) {
					//selectedAddressesText.setLength(0);
					//selectedAddressesText.append("jsdebug: Your selection includes "+sel.length+" element(s):"+System.getProperty("line.separator"));
					
					for (int i = 0; i < sel.length; i++) {
						Kontakt k = (Kontakt) sel[i];

						/*
						 * Synthesize the address lines to output from the entries in Kontakt k;
						 * added to implement the output format desired for the copyAddressToClipboard()
						 * buttons added to version 2.1.6.js as of 2012-01-28ff
					 	 *
						 * We might synthesize our own "Anschrift" for each Kontakt,
						 * completely according to our own requirements,
						 * OR use any of the methods defined for Kontakt like:
						 * getLabel...(), getPostAnschrift, createStandardAnschrift, List<BezugsKontakt>... -
						 * 
						 * The Declaration of Kontakt with field definitions is available in Kontakt.java, please look
						 * therein for additional details, please. Click-Right -> Declaration on Kontakt in Eclipse works.
						 * You can also look above to see the fields that printList would use.
						 */ 

						//selectedAddressesText.append("jsdebug: Item "+Integer.toString(i)+" "+k.toString()+System.getProperty("line.separator"));

						//getPostAnschriftPhoneFaxEmail() already returns a line separator after the address
						//The first parameter controls multiline or single line output
						//The second parameter controls whether the phone numbers shall be included
						selectedAddressesText.append(k.getPostAnschriftPhoneFaxEmail(true,true));

						//Add another empty line (or rather: paragraph), if at least one more address will follow.
						if (i<sel.length-1) {
							selectedAddressesText.append(System.getProperty("line.separator"));
										
						}
					}		//js: for each element in sel do

					/*
					 * 20120130js:
					 * I would prefer to move the following code portions down behind the "if sel not empty" block,
					 * so that (a) debugging output can be produced and (b) the clipboard will be emptied
					 * when NO addresses have been selected. I did this to avoid the case where a user would assume
					 * they had selected some address, copied data to the clipboard, and pasted them - and, even
					 * when they erred about their selection, which was indeed empty, they would not immediately
					 * notice that because some (old, unchanged) content would still come out of the clipboard.
					 * 
					 * But if I do so, and there actually is no address selected, I get an error window:
					 * Unhandled Exception ... not valid. So to avoid that message without any further research
					 * (I need to get this work fast now), I move the code back up and leave the clipboard
					 * unchanged for now, if no addresses had been selected to process.
					 * 
					 * (However, I may disable the toolbar icon / menu entry for this action in that case later on.) 
				 	 */				 	 
					
				    //System.out.print("jsdebug: selectedAddressesText: \n"+selectedAddressesText+"\n");
					
					//Adopted from BestellView.exportClipboardAction:
					//Copy some generated object.toString() to the clipoard
					
					Clipboard clipboard = new Clipboard(UiDesk.getDisplay());
					TextTransfer textTransfer = TextTransfer.getInstance();
					Transfer[] transfers = new Transfer[] {	textTransfer };
					Object[] data = new Object[] {
						selectedAddressesText.toString()
					};
					clipboard.setContents(data, transfers);
					clipboard.dispose();
				}			//js: if sel not empty
			};  	//js: copySelectedAddressesToClipboardAction.run()

		};
	
	}

	class KontaktLabelProvider extends DefaultLabelProvider {

		@Override
		public String getText(Object element) {
			String[] fields = new String[] { Kontakt.FLD_NAME1, Kontakt.FLD_NAME2, Kontakt.FLD_NAME3,
					Kontakt.FLD_STREET, Kontakt.FLD_ZIP, Kontakt.FLD_PLACE, Kontakt.FLD_PHONE1 };
			String[] values = new String[fields.length];
			((Kontakt) element).get(fields, values);
			return StringTool.join(values, StringConstants.COMMA);
		}

		@Override
		public Image getColumnImage(Object element, int columnIndex) {
			return null;
		}
	}
}
