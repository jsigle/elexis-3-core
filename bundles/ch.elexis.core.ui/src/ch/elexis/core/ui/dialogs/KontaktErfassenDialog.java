/*******************************************************************************
 * Copyright (c) 2007-2010, medshare and Elexis, Portions (c) 2021 Jörg M. Sigle
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    M. Imhof - initial implementation
 *    G. Weirich - added Anschrift
 *    J. Sigle - added (akademischer) Titel incl. automatic split and
 *    			 transfer from updated medshare directories result
 * 
 *******************************************************************************/
package ch.elexis.core.ui.dialogs;

import java.util.List;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.events.HyperlinkAdapter;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.widgets.Hyperlink;

import ch.elexis.admin.AccessControlDefaults;
import ch.elexis.core.data.activator.CoreHub;
import ch.elexis.core.data.events.ElexisEventDispatcher;
import ch.elexis.core.ui.UiDesk;
import ch.elexis.core.ui.icons.ImageSize;
import ch.elexis.core.ui.icons.Images;
import ch.elexis.core.ui.util.SWTHelper;
import ch.elexis.data.Anwender;
import ch.elexis.data.Kontakt;
import ch.elexis.data.Labor;
import ch.elexis.data.Mandant;
import ch.elexis.data.Organisation;
import ch.elexis.data.Patient;
import ch.elexis.data.Person;
import ch.elexis.data.Query;
import ch.rgw.tools.ExHandler;
import ch.rgw.tools.StringTool;
import ch.rgw.tools.TimeTool;
import ch.rgw.tools.TimeTool.TimeFormatException;

public class KontaktErfassenDialog extends TitleAreaDialog {
	
	private Button bOrganisation, bLabor, bPerson, bPatient, bAnwender, bMandant;
	
	Kontakt newKontakt = null;
	
	String[] fld;
	//20210421js: Size and contents of fld[] should be kept in sync with HINT_...
	//at the beginning of KontaktSelektor.java class Kontaktselektor,
	//otherwise fld[HINT_AKTITEL] may easily point beyond fld[fld.length()-1].
	Text tName, tVorname, tGebDat, tStrasse, tPlz, tOrt, tTel, tZusatz, tFax, tEmail, /*tSex, tPatient,*/ tTitel;
	Combo cbSex;
	Label lName, lVorname, lZusatz, lTitel;
	Hyperlink hlAnschrift;
	
	public KontaktErfassenDialog(final Shell parent, final String[] fields){
		super(parent);
		//20210403js: incoming fields.length is 10, that's why fld does also get length 10 below,
		//and that's why associating anything with fld[n>9] creates an out-of-bounds error,
		//and that's why HINTSIZE and HINT_... above with n>9 are currently of no use.
		System.out.println("KontaktErfassenDialog: incoming fields.length="+fields.length);
		fld = fields;
	}
	
	@Override
	protected Control createDialogArea(final Composite parent){
		Composite typeComp = new Composite(parent, SWT.NONE);
		typeComp.setLayoutData(SWTHelper.getFillGridData(1, true, 1, true));
		typeComp.setLayout(new GridLayout(1, false));
		
		Composite cTypes = UiDesk.getToolkit().createComposite(typeComp, SWT.BORDER);
		bOrganisation =
			UiDesk.getToolkit().createButton(cTypes, Messages.KontaktErfassenDialog_organization, //$NON-NLS-1$
				SWT.CHECK);
		bOrganisation.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e){
				bOrganisationChanged(bOrganisation.getSelection());
			}
		});
		bLabor = UiDesk.getToolkit().createButton(cTypes, Messages.KontaktErfassenDialog_labor,
			SWT.CHECK); //$NON-NLS-1$
		bLabor.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e){
				bLaborChanged(bLabor.getSelection());
			}
		});
		bPerson = UiDesk.getToolkit().createButton(cTypes, Messages.KontaktErfassenDialog_person,
			SWT.CHECK); //$NON-NLS-1$
		bPerson.setSelection(true);
		bPerson.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e){
				bPersonChanged(bPerson.getSelection());
			}
		});
		bPatient = UiDesk.getToolkit().createButton(cTypes, Messages.KontaktErfassenDialog_patient,
			SWT.CHECK); //$NON-NLS-1$
		bPatient.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e){
				bPatientChanged(bPatient.getSelection());
			}
		});
		if (fld.length > KontaktSelektor.HINT_PATIENT) {
			if (!StringTool.isNothing(fld[KontaktSelektor.HINT_PATIENT])) {
				bPatient.setSelection(true);
			}
		}
		bAnwender = UiDesk.getToolkit().createButton(cTypes, Messages.KontaktErfassenDialog_user,
			SWT.CHECK); //$NON-NLS-1$
		bAnwender.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e){
				bAnwenderChanged(bAnwender.getSelection());
			}
		});
		bMandant = UiDesk.getToolkit().createButton(cTypes, Messages.KontaktErfassenDialog_mandant,
			SWT.CHECK); //$NON-NLS-1$
		bMandant.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e){
				bMandantChanged(bMandant.getSelection());
			}
		});
		// Not everybody may create users and mandators
		if (!CoreHub.acl.request(AccessControlDefaults.ACL_USERS)) {
			bMandant.setEnabled(false);
			bAnwender.setEnabled(false);
		}
		cTypes.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
		
		cTypes.setLayout(new FillLayout());
		
		Composite ret = new Composite(parent, SWT.NONE);
		ret.setLayoutData(SWTHelper.getFillGridData(1, true, 1, true));
		ret.setLayout(new GridLayout(2, false));
		
		
		//TODO: 20210403js: Added this; however THIS causes problems below
		//when used as index into fld[] whose length is 10.
		//For whatever reason, and whereever it is instantiated/initialized.
		//I couldn't manage to find this out in serveral hours of time
		//and I give it up right now because I can't afford to search further.
		//Wouldn't it be good if this program was not made from a labyrinth
		//of code snippets, first hacked into pieces, then mixed and glued together...

		//TODO: 20210403js: fld.length ergibt hier nur 10 oder 11, wohl je
		//nachdem, woher der Dialog aufgerufen wird. Dementsprechend werden
		//dann unterschiedlich viele Felder angezeigt oder bPatient wohl vorbelegt -
		//siehe ein bisschen weiter oben. --- LEIDER wird dem Dialog dementsprechend
		//auch ein fld mit unterschiedlich vielen Feldern angeliefert, UND mindestens
		//hier in dieser Prozedur, möglicherweise aber auch sonstwo wird anhand
		//von fld.length auch ermittelt, was hier und dort wie gemacht werden
		//soll, UND die Inhalte der Felder werden gerade nicht über schön
		//definierte arrays aus benannten Feldern bestimmt, sondern über
		//numerische Indizes, deren Bedeutung dann in FLD_... und HINT_...
		//versteckt wird. HMPF. -- UND (!!!) manche aufrufenden Methoden
		//bezeichnen die Felder direkt über ihre Nummer (also fld[1], fld[2] usw.)
		//Also: Es wird ziemlich schwer und ziemlich riskant, hier z.B. ein Feld
		//"(akademischer) Titel" möglichst noch VOR den Feldern für Name und Vorname
		//einzu fügen - so dass rundum nicht plötzlich irgendwelche Teilfunktionalität
		//ausfällt oder für die falschen Kontaktarten aufgerufen wird, oder dass
		//plötzlich "Otto Müller" bei Strasse und Ort landet (oder ähnliches).
		//WAS ich jetzt vorläufig mache:
		//Ich mache mal experimentell fld[] um so viele Felder länger,
		//wie bis zu fld[HINT_AKTITEL] nötig sind - d.h. das wird IMMER
		//mit HINTSIZE feldern enden, weil ich HINT_AKTITEL in KontaktSelektor
		//ja nun mit der ersten freien Nummer = HINTEN angefügt habe.
		//Mal schauen, was dann passiert.
		//Die angefügten Felder belege ich mit "" vor.

		//20210403js: Wenn fld zu klein ist,...
		//System.out.println("createDialogArea: fld.length()="+fld.length);
		if (fld.length<KontaktSelektor.HINT_AKTITEL) {
			//lege ausreichend grosses temp array an.
			String[] tmpfld = new String[KontaktSelektor.HINT_AKTITEL+1];
			//kopiere die vorhandenen Daten von fld nach tmpfld...
			System.arraycopy(fld,0,tmpfld,0,fld.length);
			//initialisiere die neu hinzugekommenen Strings
			for (int i=fld.length; i<tmpfld.length; i++)
				tmpfld[i] = "";
			//lasse fld auf das neue array zeigen, das alte entfernt der garbage collector
			fld = tmpfld;			
		}
		//SO. JETZT kann ich beruhigt etwas mit dem Feld fld[KontaktSelektor.HINT_AKTUELL] anfangen.
		//System.out.println("createDialogArea: fld.length()="+fld.length);
		lTitel = new Label(ret, SWT.NONE);
		lTitel.setText(Messages.KontaktErfassenDialog_akTitel); //$NON-NLS-1$
		tTitel = new Text(ret, SWT.BORDER);
		//Ob das fld jemals hier so hereinkommt, DASS da schon irgendwas drinstehen würde,
		//was nun dargestellt werden dürfte - weiss ich nicht. Eigentlich ist aber genau das
		//EIN Sinn dieser Erweiterung, denn ich möchte einen aus dem Ergebnis der wiederbelebten
		//medshare directories Suche gefundenen Titel hier bereits vorbelegen - und ZWEITENS
		//will ich haben, dass man Kontaktdaten von Kollegen oder auch Patienten mit einem
		//akademischen oder professionellen Titel SOFORT in einem Dialog eingeben kann, und
		//nicht (wie aohl bisher) zuerst einen Kontakt ohne diesen Titel anlegen, und danach
		//nochmal in die View Kontakte Details gehen und dort den Titel nachtragen muss.
		tTitel.setText(fld[KontaktSelektor.HINT_AKTITEL]);
		tTitel.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
		tTitel.setTextLimit(80);
		//NACH dieser Erweiterung wird nun schon mal ein Feld (akademischer) Titel OHNE
		//java.out-of-bounds-Error im Dialog angezeigt, der erscheint, wenn man in der
		//Kontakte View auf (+) klickt, und zwar wie von mir gewünscht ganz oben :-)
		//NICHT angezeigt wird das Feld leider, nach Klick auf (+) in der Patientenliste :-(

		
		lName = new Label(ret, SWT.NONE);
		lName.setText(Messages.KontaktErfassenDialog_name); //$NON-NLS-1$
		tName = new Text(ret, SWT.BORDER);
		tName.setText(fld[KontaktSelektor.HINT_NAME]);
		tName.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
		tName.setTextLimit(80);
		
		lVorname = new Label(ret, SWT.NONE);
		lVorname.setText(Messages.KontaktErfassenDialog_firstName); //$NON-NLS-1$
		tVorname = new Text(ret, SWT.BORDER);
		tVorname.setText(
			fld[KontaktSelektor.HINT_FIRSTNAME] == null ? "" : fld[KontaktSelektor.HINT_FIRSTNAME]); //$NON-NLS-1$
		tVorname.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
		tVorname.setTextLimit(80);
		
		lZusatz = new Label(ret, SWT.NONE);
		lZusatz.setText(Messages.KontaktErfassenDialog_zusatz); //$NON-NLS-1$
		tZusatz = new Text(ret, SWT.BORDER);
		tZusatz.setText(fld.length > KontaktSelektor.HINT_ADD ? fld[KontaktSelektor.HINT_ADD] : ""); //$NON-NLS-1$
		tZusatz.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
		
		new Label(ret, SWT.NONE).setText(Messages.PatientErfassenDialog_sex);//$NON-NLS-1$
		cbSex = new Combo(ret, SWT.SINGLE);
		cbSex.setItems(new String[] {
			Messages.KontaktErfassenDialog_male, Messages.KontaktErfassenDialog_female
		}); //$NON-NLS-1$ //$NON-NLS-2$
		
		if (fld.length <= KontaktSelektor.HINT_SEX || fld[KontaktSelektor.HINT_SEX].length() == 0) {
			if (StringTool.isNothing(fld[KontaktSelektor.HINT_FIRSTNAME])) {
				cbSex.select(0);
			} else {
				cbSex.select(StringTool.isFemale(fld[KontaktSelektor.HINT_FIRSTNAME]) ? 1 : 0);
			}
		} else {
			cbSex.select(fld[KontaktSelektor.HINT_SEX].equals(Person.MALE) ? 0 : 1);
		}
		
		new Label(ret, SWT.NONE).setText(Messages.KontaktErfassenDialog_birthDate); //$NON-NLS-1$
		tGebDat = new Text(ret, SWT.BORDER);
		tGebDat.setText(
			fld[KontaktSelektor.HINT_BIRTHDATE] == null ? "" : fld[KontaktSelektor.HINT_BIRTHDATE]); //$NON-NLS-1$
		tGebDat.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
		tGebDat.setTextLimit(8);
		
		new Label(ret, SWT.NONE).setText(Messages.PatientErfassenDialog_street); //$NON-NLS-1$
		tStrasse = new Text(ret, SWT.BORDER);
		tStrasse.setText(
			fld.length > KontaktSelektor.HINT_STREET ? fld[KontaktSelektor.HINT_STREET] : ""); //$NON-NLS-1$
		tStrasse.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
		tStrasse.setTextLimit(80);
		
		new Label(ret, SWT.NONE).setText(Messages.PatientErfassenDialog_zip); //$NON-NLS-1$
		tPlz = new Text(ret, SWT.BORDER);
		tPlz.setText(fld.length > KontaktSelektor.HINT_ZIP ? fld[KontaktSelektor.HINT_ZIP] : ""); //$NON-NLS-1$
		tPlz.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
		tPlz.setTextLimit(6);
		
		new Label(ret, SWT.NONE).setText(Messages.PatientErfassenDialog_city); //$NON-NLS-1$
		tOrt = new Text(ret, SWT.BORDER);
		tOrt.setText(
			fld.length > KontaktSelektor.HINT_PLACE ? fld[KontaktSelektor.HINT_PLACE] : ""); //$NON-NLS-1$
		tOrt.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
		tOrt.setTextLimit(50);
		
		new Label(ret, SWT.NONE).setText(Messages.PatientErfassenDialog_phone); //$NON-NLS-1$
		tTel = new Text(ret, SWT.BORDER);
		tTel.setText(fld.length > 6 ? fld[6] : ""); //$NON-NLS-1$
		tTel.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
		tTel.setTextLimit(30);
		
		new Label(ret, SWT.NONE).setText(Messages.KontaktErfassenDialog_fax); //$NON-NLS-1$
		tFax = new Text(ret, SWT.BORDER);
		tFax.setText(fld.length > 8 ? fld[8] : ""); //$NON-NLS-1$
		tFax.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
		tFax.setTextLimit(30);
		
		new Label(ret, SWT.NONE).setText(Messages.KontaktErfassenDialog_email); //$NON-NLS-1$
		tEmail = new Text(ret, SWT.BORDER);
		tEmail.setText(fld.length > 9 ? fld[9] : ""); //$NON-NLS-1$
		tEmail.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
		tEmail.setTextLimit(80);
		new Label(ret, SWT.NONE).setText(Messages.KontaktErfassenDialog_postanschrift); //$NON-NLS-1$
		hlAnschrift = UiDesk.getToolkit().createHyperlink(ret,
			Messages.KontaktErfassenDialog_postalempty, SWT.NONE); //$NON-NLS-1$
		hlAnschrift.addHyperlinkListener(new HyperlinkAdapter() {
			
			@Override
			public void linkActivated(HyperlinkEvent e){
				createKontakt();
				AnschriftEingabeDialog aed = new AnschriftEingabeDialog(getShell(), newKontakt);
				aed.create();
				SWTHelper.center(getShell(), aed.getShell());
				aed.open();
				hlAnschrift.setText(newKontakt.getPostAnschrift(false));
			}
			
		});
		hlAnschrift.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
		return ret;
	}
	
	@Override
	public void create(){
		super.create();
		setMessage(Messages.KontaktErfassenDialog_message); //$NON-NLS-1$
		setTitle(Messages.KontaktErfassenDialog_subTitle); //$NON-NLS-1$
		getShell().setText(Messages.KontaktErfassenDialog_title); //$NON-NLS-1$
		setTitleImage(Images.IMG_LOGO.getImage(ImageSize._75x66_TitleDialogIconSize));
	}
	
	protected void bOrganisationChanged(boolean isSelected){
		bOrganisation.setSelection(isSelected);
		if (isSelected) {
			bPersonChanged(false);
			lName.setText(Messages.KontaktErfassenDialog_bezeichnung);//$NON-NLS-1$
			lVorname.setText(Messages.KontaktErfassenDialog_zusatz); //$NON-NLS-1$
			lZusatz.setText(Messages.KontaktErfassenDialog_ansprechperson); //$NON-NLS-1$
			cbSex.setEnabled(false);
			lName.getParent().layout();
		} else {
			bLaborChanged(false);
		}
	}
	
	protected void bLaborChanged(boolean isSelected){
		bLabor.setSelection(isSelected);
		if (isSelected) {
			bOrganisationChanged(true);
			lZusatz.setText(Messages.KontaktErfassenDialog_laborleiter); //$NON-NLS-1$
			lName.getParent().layout();
		}
	}
	
	protected void bPersonChanged(boolean isSelected){
		bPerson.setSelection(isSelected);
		if (isSelected) {
			bOrganisationChanged(false);
			lName.setText(Messages.KontaktErfassenDialog_name);//$NON-NLS-1$
			lVorname.setText(Messages.KontaktErfassenDialog_firstName); //$NON-NLS-1$
			lTitel.setText(Messages.KontaktErfassenDialog_akTitel); //$NON-NLS-1$
			lZusatz.setText(Messages.KontaktErfassenDialog_zusatz); //$NON-NLS-1$
			cbSex.setEnabled(true);
			lName.getParent().layout();
		} else {
			bAnwenderChanged(false);
			bMandantChanged(false);
			bPatientChanged(false);
		}
	}
	
	protected void bAnwenderChanged(boolean isSelected){
		bAnwender.setSelection(isSelected);
		if (isSelected) {
			bPatientChanged(false);
			bPersonChanged(true);
		} else {
			bMandantChanged(false);
		}
	}
	
	protected void bMandantChanged(boolean isSelected){
		bMandant.setSelection(isSelected);
		if (isSelected) {
			bAnwenderChanged(true);
		}
	}
	
	protected void bPatientChanged(boolean isSelected){
		bPatient.setSelection(isSelected);
		if (isSelected) {
			bAnwenderChanged(false);
			bPersonChanged(true);
		}
	}
	
	private void createKontakt(){
		String[] ret = new String[9];
		ret[8] = tTitel.getText();		//20210403js
		ret[0] = tName.getText();
		ret[1] = tVorname.getText();
		int idx = cbSex.getSelectionIndex();
		if (idx == -1) {
			SWTHelper.showError(Messages.KontaktErfassenDialog_geschlechtFehlt_title, //$NON-NLS-1$
				Messages.KontaktErfassenDialog_geschlechtFehlt_msg); //$NON-NLS-1$
			return;
		}
		ret[2] = cbSex.getItem(idx);
		ret[3] = tGebDat.getText();
		try {
			if (!StringTool.isNothing(ret[3])) {
				new TimeTool(ret[3], true);
			}
			ret[4] = tStrasse.getText();
			ret[5] = tPlz.getText();
			ret[6] = tOrt.getText();
			ret[7] = tTel.getText();
			if (newKontakt == null) {
				Query<Kontakt> qbe = new Query<Kontakt>(Kontakt.class);
				qbe.add("Bezeichnung1", "=", ret[0]); //$NON-NLS-1$ //$NON-NLS-2$
				qbe.add("Bezeichnung2", "=", ret[1]); //$NON-NLS-1$ //$NON-NLS-2$
				List<Kontakt> list = qbe.execute();
				if ((list != null) && (!list.isEmpty())) {
					Kontakt k = list.get(0);
					if (bOrganisation.getSelection() && k.istOrganisation()) {
						if (bLabor.getSelection()) {
							k.set("istOrganisation", "1"); //$NON-NLS-1$ //$NON-NLS-2$
						}
						if (MessageDialog.openConfirm(getShell(),
							Messages.KontaktErfassenDialog_organisationExistiert_title, //$NON-NLS-1$
							Messages.KontaktErfassenDialog_organisationExistiert_msg) == false) { //$NON-NLS-1$
							super.okPressed();
							return;
						}
					}
					if (k.istPerson()) {
						if (bAnwender.getSelection()) {
							k.set("istAnwender", "1"); //$NON-NLS-1$ //$NON-NLS-2$
						}
						if (bMandant.getSelection()) {
							k.set("istMandant", "1"); //$NON-NLS-1$ //$NON-NLS-2$
						}
						if (bPatient.getSelection()) {
							k.set("istPatient", "1"); //$NON-NLS-1$ //$NON-NLS-2$
						}
						if (MessageDialog.openConfirm(getShell(),
							Messages.KontaktErfassenDialog_personExisitiert_title, //$NON-NLS-1$
							Messages.KontaktErfassenDialog_personExisitiert_msg) == false) { //$NON-NLS-1$
							super.okPressed();
							return;
						}
					}
				}
				
				/**
				 * Neuer Kontakt erstellen. Reihenfolge der Abfrage ist Wichtig, da ein Anwender
				 * auch ein Mandant sein kann. "Organisation", - "Labor", "Person" - "Patient" -
				 * "Anwender" - "Mandant"
				 */
				if (bMandant.getSelection()) {
					newKontakt = new Mandant(ret[0], ret[1], ret[3], ret[2]);
					newKontakt.set("Zusatz", tZusatz.getText()); //$NON-NLS-1$
				} else if (bAnwender.getSelection()) {
					newKontakt = new Anwender(ret[0], ret[1], ret[3], ret[2]);
					newKontakt.set("Zusatz", tZusatz.getText()); //$NON-NLS-1$
				} else if (bPatient.getSelection()) {
					newKontakt = new Patient(ret[0], ret[1], ret[3], ret[2]);
					newKontakt.set("Zusatz", tZusatz.getText()); //$NON-NLS-1$
				} else if (bPerson.getSelection()) {
					newKontakt = new Person(ret[0], ret[1], ret[3], ret[2]);
					newKontakt.set("Zusatz", tZusatz.getText()); //$NON-NLS-1$
					newKontakt.set("Titel", tTitel.getText());	//20210403js
				} else if (bLabor.getSelection()) {
					newKontakt = new Labor(ret[0], ret[0]);
					newKontakt.set("Zusatz1", ret[1]); //$NON-NLS-1$
					newKontakt.set("Ansprechperson", tZusatz.getText()); //$NON-NLS-1$
				} else if (bOrganisation.getSelection()) {
					newKontakt = new Organisation(ret[0], ret[1]);
					newKontakt.set("Ansprechperson", tZusatz.getText()); //$NON-NLS-1$
				} else {
					MessageDialog.openInformation(getShell(),
						Messages.KontaktErfassenDialog_unbekannterTyp_title, //$NON-NLS-1$
						Messages.KontaktErfassenDialog_unbekannterTyp_msg); //$NON-NLS-1$
					return;
				}
			}
			if (CoreHub.getLocalLockService().acquireLock(newKontakt).isOk()) {
				newKontakt.set(new String[] {
					"Strasse", "Plz", "Ort", "Telefon1", "Fax", "E-Mail" //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$
				}, new String[] {
					ret[4], ret[5], ret[6], ret[7], tFax.getText(), tEmail.getText()
				});
				
				ElexisEventDispatcher.fireSelectionEvent(newKontakt);
				CoreHub.getLocalLockService().releaseLock(newKontakt);
			}
		} catch (TimeFormatException e) {
			ExHandler.handle(e);
			SWTHelper.showError(Messages.KontaktErfassenDialog_falschesDatum_title, //$NON-NLS-1$
				Messages.KontaktErfassenDialog_falschesDatum_msg); //$NON-NLS-1$
			return;
		}
	}
	
	@Override
	protected void okPressed(){
		createKontakt();
		super.okPressed();
	}
	
	public Kontakt getResult(){
		return newKontakt;
	}
}
