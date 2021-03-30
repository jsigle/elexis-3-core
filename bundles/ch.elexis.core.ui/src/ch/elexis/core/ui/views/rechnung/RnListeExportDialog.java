/*******************************************************************************
 * Copyright (c) 2013-2021, Joerg Sigle
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    J. Sigle   - Multiple improvements to RnActions in 2013
 *    			 - 201510, 201512: added Liste exportieren, um eine Rechnungsliste zu exportieren
 *    			 - 201512: Rechnungen nicht mehrfach im Verarbeitungsergebnis aufführen, wenn zuvor aufgeklappt wurde,
 *    			   und eine Rechnung auf Ebene von Patient/Fall/Rechnung effektiv bis zu 3 x markiert ist.
 *    N. Giger	 - ca. 2018..2019: Adopted from 2.1.7js to 3.x, moved into separate class, changing code losing correct functionality
 *    J. Sigle	 - Restored original implementation, adopted to new 3.x environment, restoring correct functionality   
 * 
 *******************************************************************************/

package ch.elexis.core.ui.views.rechnung;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.elexis.core.data.activator.CoreHub;
import ch.elexis.core.ui.text.ITextPlugin.ICallback;
import ch.elexis.core.ui.util.SWTHelper;
import ch.elexis.data.Fall;
import ch.elexis.data.Konsultation;
import ch.elexis.data.Patient;
import ch.elexis.data.Rechnung;
import ch.elexis.data.RnStatus;
import ch.elexis.data.Zahlung;
import ch.elexis.scripting.CSVWriter;
import ch.rgw.tools.ExHandler;
import ch.rgw.tools.Money;
import ch.rgw.tools.Tree;


//20210330js: Meine originale Version wiederhergestellt - die war ursprünglich einfach
//eine Methode in RnActions.java - Niklaus hat sie beim portieren in ein eigenes File
//als eigene Klasse ausgelagert (warum auch immer). - Leider ist seine Version nicht äquivalent
//zum Original und das von ihm erzeugte Ergebnis lässt sich z.B. mit Excel nicht brauchbar
//öffnen. Unten steht die von Niklaus veränderte und nicht mehr brauchbare Fassung auskommentiert.
//
//Hier habe ich wenigstens die Original-Implementation aus 2.1.7js wiederhergestellt,
//angepasst für veränderte getMessages etc., und das Logging von System.out.printl()
//auf den von Niklaus bevorzugten Logger umgestellt.
//
//In meinem Dialog zur Auswahl des Zielordners wird das verwendete Format angezeigt.
//Diese Fassung arbeitet jetzt jedenfalls so, wie sie soll:

//201512211341js: Info: This dialog starts the generation of output ONLY AFTER [OK] has been pressed. 
class RnListeExportDialog extends TitleAreaDialog implements ICallback {
	private Logger log = LoggerFactory.getLogger(RnActions.class);
		ArrayList<Rechnung> rnn;
		
		//201512211459js: Siehe auch RechnungsDrucker.java - nach dortigem Vorbild modelliert.
		//Zur Kontrolle es Ausgabeverzeichnisses, mit permanentem Speichern.
		//ToDo: Durchgängig auf externe Konstanten umstellen, wie dort gezeigt, u.a. bei Hub.LocalCfg Zugriffen.
		private Button bSaveFileAs;
		String RnListExportDirname = CoreHub.localCfg.get("rechnung/RnListExportDirname", null);
		Text tDirName;	
		
		public RnListeExportDialog(final Shell shell, final Object[] tree){
			super(shell);
			rnn = new ArrayList<Rechnung>(tree.length);
			for (Object o : tree) {
				if (o instanceof Tree) {
					Tree tr = (Tree) o;
					if (tr.contents instanceof Rechnung) {
						tr = tr.getParent();
					}
					if (tr.contents instanceof Fall) {
						tr = tr.getParent();
					}
					if (tr.contents instanceof Patient) {
						for (Tree tFall : (Tree[]) tr.getChildren().toArray(new Tree[0])) {
							Fall fall = (Fall) tFall.contents;
							for (Tree tRn : (Tree[]) tFall.getChildren().toArray(new Tree[0])) {
								Rechnung rn = (Rechnung) tRn.contents;
								//201512211302js: Rechnungen sollten nicht doppelt im Verarbeitungsergebnis auftreten,
								//nur weil aufgeklappt und dann bis zu 3x etwas vom gleichen Patienten/Fall/Rechnung markiert war.
								if (!rnn.contains(rn)) {		//deshalb prüfen, ob die rechnung schon drin ist, bevor sie hinzugefügt wird.
									rnn.add(rn);
								}
							}
						}
					}
				}
			}
			
		}		
		
		//ToDo: We probably don't need an overwriting close() method here, because we don't use the text plugin. !!!");
		//20151013js: After copying RnListePrint to RnListeExport, removed most content from this close method.
		//201512210059js: Improved exported fields / content, to reseble what's available in View Rechnungsdetails
		//and meet the requirements for the exported table. 
		@Override
		public boolean close(){
			//Call the original overwritten close method?
			boolean ret = super.close();
			
			log.debug("RnListeExportDialog: close(): begin");
			log.debug("RnListeExportDialog: !!!! ToDo: We probably don't need an overwriting close() method here, because we don't use the text plugin. !!!");
			log.debug("RnListeExportDialog: close(): about to return ret");
			return ret;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		protected Control createDialogArea(final Composite parent){
			log.debug("RnListeExportDialog: createDialogArea(): begin");
			Composite ret = new Composite(parent, SWT.NONE);
			ret.setLayout(new FillLayout());
			ret.setLayoutData(SWTHelper.getFillGridData(1, true, 1, true));
	
			
			//201512211432js: Siehe auch Rechnungsdrucker.java public class RechnungsDrucker.createSettingsControl()
			//TODO: Auf Konstante umstellen, dann braucht's allerdings den Austausch weiterer Module bei Installation!!!
			log.debug("TODO: RnActions.java.RnListeExportDialog.CreateSettingsControl(): MEHRFACH wie in RechnungsDrucker.java Auf Konstante für PreferenceConstants.RNLIST_EXPORTDIR für RnListExportDirname umstellen, dann braucht's allerdings den Austausch weiterer Module bei Installation!!!");
			
			//String RnListExportDirname = Hub.localCfg.get(PreferenceConstants.RNLIST_EXPORTDIR, null);
			
			Group cSaveCopy = new Group(ret, SWT.NONE);
			//ToDo: Umstellen auf externe Konstante!
			cSaveCopy.setText("Export als Tabelle in Textdatei: RnListExport-yyyyMMddhhmmss.txt, ColSep=TAB, LineSep=CR, \"um alle Felder\", Multiline-Inhalte in Feldern");
			cSaveCopy.setLayout(new GridLayout(2, false));
			bSaveFileAs = new Button(cSaveCopy, SWT.CHECK);
			//ToDo: Umstellen auf externe Konstante!
			bSaveFileAs.setText("Textdatei erstellen");
			bSaveFileAs.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
			//ToDo: Umstellen auf externe Konstante! - auch noch viel weiter unten
			bSaveFileAs.setSelection(CoreHub.localCfg.get("rechnung/RnListExportDirname_bSaveFileAs", true));
			bSaveFileAs.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e){
					CoreHub.localCfg.set("rechnung/RnListExportDirname_bSaveFileAs", bSaveFileAs.getSelection());
				}
				
			});

			Button bSelectFile = new Button(cSaveCopy, SWT.PUSH);
			bSelectFile.setText(Messages.RnActions_exportListDirName);
			bSelectFile.setLayoutData(SWTHelper.getFillGridData(2, false, 1, false));
			bSelectFile.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e){
					DirectoryDialog ddlg = new DirectoryDialog(parent.getShell());
					RnListExportDirname = ddlg.open();
					if (RnListExportDirname == null) {
						SWTHelper.alert(Messages.RnActions_exportListDirNameMissingCaption,
								Messages.RnActions_exportListDirNameMissingText);
					} else {
						//ToDo: Umstellen auf externe Konstante!
						CoreHub.localCfg.set("rechnung/RnListExportDirname", RnListExportDirname);
						tDirName.setText(RnListExportDirname);
					}
				}
			});
			
			tDirName = new Text(cSaveCopy, SWT.BORDER | SWT.READ_ONLY);
			tDirName.setText(CoreHub.localCfg.get("rechnung/RnListExportDirname", "")); //$NON-NLS-1$
			tDirName.setLayoutData(SWTHelper.getFillGridData(2, true, 1, false));
			
			
			
			
			log.debug("RnListeExportDialog: createDialogArea(): about to return ret");
			return ret;
		}
			
		@Override
		public void create() {
			super.create();
			log.debug("RnListeExportDialog: create(): begin");
			getShell().setText(Messages.RnActions_billsList); //$NON-NLS-1$
			setTitle(Messages.RnActions_exportListCaption); //$NON-NLS-1$
			setMessage(Messages.RnActions_exportListMessage); //$NON-NLS-1$
			getShell().setSize(900, 700);
			
			//TODO: 20210330js: Diese Zeile geht jetzt nicht mehr. Wofür war sie da? Ist sie nötig?
			//SWTHelper.center(CoreHub.plugin.getWorkbench().getActiveWorkbenchWindow().getShell(),getShell());
			log.debug("RnListeExportDialog: create(): end");
		}
		
		@Override
		protected void okPressed(){
			super.okPressed();
			log.debug("RnListeExportDialog: okPressed(): begun, post super");
			if (CoreHub.localCfg.get("rechnung/RnListExportDirname_bSaveFileAs", true)) CSVWriteTable();
			log.debug("RnListeExportDialog: okPressed(): about to end");
		}
		
		public void save(){
			// TODO Auto-generated method stub
			log.debug("RnListeExportDialog: save(): begun, about to end");			
		}
		
		public boolean saveAs(){
			// TODO Auto-generated method stub
			log.debug("RnListeExportDialog: saveAs(): begun, about to return false");			
			return false;
		}
		
		
		OutputStreamWriter CSVWriter;
		
		//201512210516js: Zur Ausgabe eines Strings mit gestrippten enthaltenen und hinzugefügten umgebenden Anführungszeichen für den CSV-Ouptut
		private void CSVWriteStringSanitized(String s) {
				String a=s;
				a.replaceAll("['\"]", "_");		//Enthaltene Anführungszeichen wegersetzen
				a.trim();
				
				try {
					CSVWriter.write('"'+a+'"');	//mit umgebenden Anführungszeichen ausgeben	//Grmblfix. In Excel 2003 als ANSI interpretiert -> Umlautfehler.
				}
				catch ( IOException e)
				{
				}
			}
			
		//201512211312js: Zur Ausgabe eines Spaltentrenners
		private void CSVWriteColSep() {
			try {
				CSVWriter.write("\t");	//mit umgebenden Anführungszeichen ausgeben
			}
			catch ( IOException e)
			{
			}
		}

		//201512211312js: Zur Ausgabe eines Spaltentrenners
		private void CSVWriteLineSep() {
			try {
				CSVWriter.write("\n");	//mit umgebenden Anführungszeichen ausgeben
			}
			catch ( IOException e)
			{
			}
		}

		//201510xxjs, 201512211312js: Produce the export table containing information about the selected bills
		public void  CSVWriteTable() {
			log.debug("RnListeExportDialog: CSVWriteTable(): begin");

			String RnListExportFileName = new SimpleDateFormat("'RnListExport-'yyyyMMddHHmmss'.txt'").format(new Date()); //kleines hh liefert 12h-Format...
		
			try {
				log.debug("RnListeExportDialog: Trying to open File "+RnListExportFileName+" for output...");
				
				//Java speichert intern als UTF-16 und gibt in Elexis standardmässig UTF-8 aus.
				//Excel (zumindest 2003) interpretiert standardmässig wohl als Windows/ANSI und liefert dann kaputte Umlaute.
				//Das gilt für Excel 2003 via drag&drop. Beim Datei-Öffnen erscheint der Dialog mit Optinen zum Import, auch zum Zeichensatz -
				//nur wird auf diesem Weg die Datei völlig zerhackt, weil etwas mit Tabs, Anführungszeichen, Newlines etc. gar nicht funktioniert.
				//Also mache ich hier mal eine Umsetzung nach iso-8859-1.
				//Wenn das NICHT nötig wäre, hätte hier gereicht: FileWriter CSVWriter; CSVWriter= new FileWriter( Dateiname );
				//Tatsächlich liefert Excel aus einer so erzeugten Datei nun korrekte Umlatue; allerdings werden wohl andere Sonderzeichen verloren gehen.
				//N.B.: Auch beim EINlesen sollte man sogleich eine Formatumsetzung auf diesem Wege mit einplanen.
				CSVWriter = new OutputStreamWriter(new FileOutputStream( RnListExportDirname+"/"+RnListExportFileName),"Cp1252");
			
				//201512211328js: Output Table Headers
				
				CSVWriteStringSanitized("Aktion?"); CSVWriteColSep();				//201512210402js: Leere Spalte zum Eintragen der gewünschten Aktion.
				CSVWriteStringSanitized("Re.Nr"); CSVWriteColSep();
				CSVWriteStringSanitized("Re.DatumRn"); CSVWriteColSep();
				CSVWriteStringSanitized("Re.DatumVon"); CSVWriteColSep();
				CSVWriteStringSanitized("Re.DatumBis"); CSVWriteColSep();
				CSVWriteStringSanitized("Re.Garant"); CSVWriteColSep();
				CSVWriteStringSanitized("Re.Total"); CSVWriteColSep();
				CSVWriteStringSanitized("Re.Offen"); CSVWriteColSep();
				CSVWriteStringSanitized("Re.StatusLastUpdate"); CSVWriteColSep();
				CSVWriteStringSanitized("Re.Status"); CSVWriteColSep();
				CSVWriteStringSanitized("Re.StatusIsActive"); CSVWriteColSep();
				CSVWriteStringSanitized("Re.StatusText"); CSVWriteColSep();
				CSVWriteStringSanitized("Re.StatusChanges"); CSVWriteColSep();
				CSVWriteStringSanitized("Re.Rejecteds"); CSVWriteColSep();
				CSVWriteStringSanitized("Re.Outputs"); CSVWriteColSep();
				CSVWriteStringSanitized("Re.Payments"); CSVWriteColSep();
				CSVWriteStringSanitized("Fall.AbrSystem"); CSVWriteColSep();
				CSVWriteStringSanitized("Fall.Bezeichnung"); CSVWriteColSep();
				CSVWriteStringSanitized("Fall.Grund"); CSVWriteColSep();
				CSVWriteStringSanitized("Pat.Nr"); CSVWriteColSep();
				CSVWriteStringSanitized("Pat.Name"); CSVWriteColSep();
				CSVWriteStringSanitized("Pat.Vorname"); CSVWriteColSep();
				CSVWriteStringSanitized("Pat.GebDat"); CSVWriteColSep();
				CSVWriteStringSanitized("Pat.LztKonsDat"); CSVWriteColSep();
				CSVWriteStringSanitized("Pat.Balance"); CSVWriteColSep();
				CSVWriteStringSanitized("Pat.GetAccountExcess"); CSVWriteColSep();
				CSVWriteStringSanitized("Pat.BillSummary.Total."); CSVWriteColSep();
				CSVWriteStringSanitized("Pat.BillSummary.Paid"); CSVWriteColSep();
				CSVWriteStringSanitized("Pat.BillSummary.Open");
				CSVWriteLineSep();
								
				//201512211340js: Produce one line for every rn in rnn
				int i;
				for (i = 0; i < rnn.size(); i++) {
					Rechnung rn = rnn.get(i);
					Fall fall = rn.getFall();
					Patient p = fall.getPatient();
					
					//201512210402js: Leere Spalte zum Eintragen der gewünschten Aktion.
					//Wenn die Aktion ganz vorne steht, reicht es später einmal, diese einzulesen, um zu wissen, ob man den Rest der Zeile verwerfen kann :-)
					
					//log.debug("");
					CSVWriteColSep();
					
					//201512210348js: Erst alles zur betroffenen Rechnung...
		
					CSVWriteStringSanitized(rn.getNr());
					CSVWriteColSep();
					CSVWriteStringSanitized(rn.getDatumRn());
					CSVWriteColSep();
					CSVWriteStringSanitized(rn.getDatumVon());
					CSVWriteColSep();
					CSVWriteStringSanitized(rn.getDatumBis());
					CSVWriteColSep();
									
					//Siehe für die Quellen von Rechnungsempfaenger und Status-/-Changes auch RechnungsBlatt.java
					//log.debug("ToDo:RnEmpfaenger");
					CSVWriteStringSanitized(fall.getGarant().getLabel());
					CSVWriteColSep();
					CSVWriteStringSanitized(rn.getBetrag().toString());
					CSVWriteColSep();
					CSVWriteStringSanitized(rn.getOffenerBetrag().toString());
					CSVWriteColSep();				
					
						{
						long luTime=rn.getLastUpdate();
						Date date=new Date(luTime);
						//ToDo: Support other date formats based upon location or configured settings
				        SimpleDateFormat df2 = new SimpleDateFormat("dd.MM.yyyy");
				        String dateText = df2.format(date);
				        CSVWriteStringSanitized(dateText.toString());
						CSVWriteColSep();
						
						
						int st=rn.getStatus();
						CSVWriteStringSanitized(Integer.toString(st));
						CSVWriteColSep();
						if (RnStatus.isActive(st)) {
							CSVWriteStringSanitized("True");
						}
						else {
							CSVWriteStringSanitized("False");
						}
						CSVWriteColSep();
						CSVWriteStringSanitized(RnStatus.getStatusText(st));
						CSVWriteColSep();
						//log.debug(rn.getStatusAtDate(now));
						//CSVWriteColSep();
						}	
						
						
					// 201512210310js: New: produce 4 fields, each with multiline content.
					
					{
						List<String> statuschgs = rn.getTrace(Rechnung.STATUS_CHANGED);
						//Kann leer sein, oder Liefert Ergebnisse wie:
						//[tt.mm.yyyy, hh:mm:ss: s, tt.mm.yy, hh:mm:ss: s, tt.mm.yy, hh:mm:ss: s]
						
						String a=statuschgs.toString();
						if (a!=null && a.length()>1) {
							//Die Uhrzeiten rauswerfen:
							a=a.replaceAll(", [0-9][0-9]:[0-9][0-9]:[0-9][0-9]", "");
							//", " durch "\n" ersetzen (Man könnte auch noch prüfen, ob danach eine Zahl/ein Datum kommt - die dann aber behalten werden muss.)
							a=a.replaceAll(", ", "\n");
							//Führende und Trailende [] bei der Ausgabe (!) rauswerfen
							CSVWriteStringSanitized(a.substring(1,a.length()-1));
						}
						CSVWriteColSep();
					}
					
					{
						if (rn.getStatus() == RnStatus.FEHLERHAFT) {
							List<String> rejects = rn.getTrace(Rechnung.REJECTED);
				
							String a=rejects.toString();
							if (a!=null && a.length()>1) {
								//Die Uhrzeiten rauswerfen:
								a=a.replaceAll(", [0-9][0-9]:[0-9][0-9]:[0-9][0-9]", "");
								//", " durch "\n" ersetzen (Man könnte auch noch prüfen, ob danach eine Zahl/ein Datum kommt - die dann aber behalten werden muss.)
								a=a.replaceAll(", ", "\n");
								//Führende und Trailende [] bei der Ausgabe (!) rauswerfen
								CSVWriteStringSanitized(a.substring(1,a.length()-1));
							}
						}
						CSVWriteColSep();
					}
		
					{
						List<String> outputs = rn.getTrace(Rechnung.OUTPUT);
							
						String a=outputs.toString();
						if (a!=null && a.length()>1) {
							//Die Uhrzeiten rauswerfen:
							a=a.replaceAll(", [0-9][0-9]:[0-9][0-9]:[0-9][0-9]", "");
							//", " durch "\n" ersetzen (Man könnte auch noch prüfen, ob danach eine Zahl/ein Datum kommt - die dann aber behalten werden muss.)
							a=a.replaceAll(", ", "\n");
							//Führende und Trailende [] bei der Ausgabe (!) rauswerfen
							CSVWriteStringSanitized(a.substring(1,a.length()-1));
						}
						CSVWriteColSep();
					}
					
					{
						List<String> payments = rn.getTrace(Rechnung.PAYMENT);
						String a=payments.toString();
						if (a!=null && a.length()>1) {
							//Die Uhrzeiten rauswerfen:
							a=a.replaceAll(", [0-9][0-9]:[0-9][0-9]:[0-9][0-9]", "");
							//", " durch "\n" ersetzen (Man könnte auch noch prüfen, ob danach eine Zahl/ein Datum kommt - die dann aber behalten werden muss.)
							a=a.replaceAll(", ", "\n");
							//Führende und Trailende [] bei der Ausgabe (!) rauswerfen
							CSVWriteStringSanitized(a.substring(1,a.length()-1));
						}
						CSVWriteColSep();
					}
					
					//201512210348js: Jetzt alles zum betroffenen Fall:
					CSVWriteStringSanitized(fall.getAbrechnungsSystem());
					CSVWriteColSep();
					CSVWriteStringSanitized(fall.getBezeichnung());
					CSVWriteColSep();
					CSVWriteStringSanitized(fall.getGrund());
					CSVWriteColSep();
					

					//201512210348js: Jetzt alles zum betroffenen Patienten:
					
					//log.debug(p.getId());
					//CSVWriteColSep();
					CSVWriteStringSanitized(p.getKuerzel());	//Das liefert die "Patientennummer, da sie frei eingebbar ist, gebe ich sie sanitized aus.
					CSVWriteColSep();
					CSVWriteStringSanitized(p.getName());
					CSVWriteColSep();
					CSVWriteStringSanitized(p.getVorname());
					CSVWriteColSep();
					CSVWriteStringSanitized(p.getGeburtsdatum());
					CSVWriteColSep();
					
					{
						//ToDo: allenfalls wieder: auf n.a. oder so setzen...
						//ToDo: Ich möcht aber wissen, ob p (dürfte eigentlich nie der Fall sein) oder nk schuld sind, wenn nichts rauskommt.
						//ToDo: Na ja, eigentlich würd ich noch lieber wissen, WARUM da manchmal nichts rauskommt, obwohl eine kons sicher vhd ist.
						String lkDatum = "p==null";
						if (p!=null)	{
							Konsultation lk=p.getLetzteKons(false);
							if (lk!=null) {lkDatum=(lk.getDatum());} else {lkDatum="lk==null";}
							//201512210211js: Offenbar manchmal n.a. - vielleicht heisst das: Kein offener Fall mit Kons? Denn berechnet wurde ja etwas!
						}
						CSVWriteStringSanitized(lkDatum);
						CSVWriteColSep();
					}
					
					//201512210134js: Money p.getKontostand() und String p.getBalance() liefern (bis auf den Variablentyp) das gleiche Ergebnis
					//log.debug(p.getKontostand());
					//CSVWriteColSep();
					CSVWriteStringSanitized(p.getBalance());		//returns: String
					CSVWriteColSep();
					CSVWriteStringSanitized(p.getAccountExcess().toString());	//returns: Money
					CSVWriteColSep();
					
					//201512210146js: Das Folgende ist aus BillSummary - dort wird dafür keine Funktion bereitgestellt,
					//ToDo: Prüfen, ob das eine Redundanz DORT und HIER ist vs. obenn erwähnter getKontostand(), getAccountExcess() etc.
					// maybe called from foreign thread
					{
						String totalText = ""; //$NON-NLS-1$
						String paidText = ""; //$NON-NLS-1$
						String openText = ""; //$NON-NLS-1$
						
						//Davon, dass p != null ist, darf man eigentlich ausgehen, da ja Rechnungen zu p gehören etc.
						if (p!= null) {
							Money total = new Money(0);
							Money paid = new Money(0);
							
							List<Rechnung> rechnungen = p.getRechnungen();
							for (Rechnung rechnung : rechnungen) {
								// don't consider canceled bills
								if (rechnung.getStatus() != RnStatus.STORNIERT) {
									total.addMoney(rechnung.getBetrag());
									for (Zahlung zahlung : rechnung.getZahlungen()) {
										paid.addMoney(zahlung.getBetrag());
									}
								}
							}
							
							Money open = new Money(total);
							open.subtractMoney(paid);
							
							totalText = total.toString();
							paidText = paid.toString();
							openText = open.toString();
						}
						
						CSVWriteStringSanitized(totalText);
						CSVWriteColSep();
						CSVWriteStringSanitized(paidText);
						CSVWriteColSep();
						CSVWriteStringSanitized(openText);
						//CSVWriteColSep();		//Nach der letzten Spalte: bitte auch kein TAB mehr ausgeben.
					}
					
					//Alle Felder zu dieser Rechnung wurden geschrieben - Zeile ist fertig.
					CSVWriteLineSep();
				}

			}
			catch ( IOException e)
			{
			}
			finally
			{
			    try
			    {
			        if ( CSVWriter != null) {
			        	log.debug("RnListeExportDialog: Trying to close File "+RnListExportFileName+".");
						CSVWriter.close( );
			        }
			    }
			    catch ( IOException e)
			    {
			    }
			}
			
			log.debug("RnListeExportDialog: CSVWriteTable(): begin");
		}
	}
	

//20210330js
//Von Niklaus beim adaptieren von 2.1.7js in 3.x verfälschte Fassung -
//das Produkt ist nun Komma-getrennt statt Tab-getrennt, vermutlich im falschen Zeichensatz,
//und wird jedenfalls von MS Excel nicht mehr korrekt geöffnet.
//Ich habe nicht die Zeit, das jetzt im Detail mit dem Original zu vergleichen und zu reparieren.
/*
//201512211341js: Info: This dialog starts the generation of output ONLY AFTER [OK] has been pressed.
class RnListeExportDialog extends TitleAreaDialog implements ICallback {
	ArrayList<Rechnung> rnn;
	private Logger log = LoggerFactory.getLogger(RnActions.class);
	private String RnListExportFileName =
			new SimpleDateFormat("'RnListExport-'yyyyMMddHHmmss'.csv'").format(new Date());
	
	//201512211459js: Siehe auch RechnungsDrucker.java - nach dortigem Vorbild modelliert.
	//Zur Kontrolle es Ausgabeverzeichnisses, mit permanentem Speichern.
	//ToDo: DurchgÃ¤ngig auf externe Konstanten umstellen, wie dort gezeigt, u.a. bei Hub.LocalCfg Zugriffen.
	String RnListExportDirname = CoreHub.localCfg.get("rechnung/RnListExportDirname", null);
	Text tDirName;
	
	public RnListeExportDialog(final Shell shell, final Object[] tree){
		super(shell);
		rnn = new ArrayList<Rechnung>(tree.length);
		for (Object o : tree) {
			if (o instanceof Tree) {
				Tree tr = (Tree) o;
				if (tr.contents instanceof Rechnung) {
					tr = tr.getParent();
				}
				if (tr.contents instanceof Fall) {
					tr = tr.getParent();
				}
				if (tr.contents instanceof Patient) {
					for (Tree tFall : (Tree[]) tr.getChildren().toArray(new Tree[0])) {
						Fall fall = (Fall) tFall.contents;
						for (Tree tRn : (Tree[]) tFall.getChildren().toArray(new Tree[0])) {
							Rechnung rn = (Rechnung) tRn.contents;
							//201512211302js: Rechnungen sollten nicht doppelt im Verarbeitungsergebnis auftreten,
							//nur weil aufgeklappt und dann bis zu 3x etwas vom gleichen Patienten/Fall/Rechnung markiert war.
							if (!rnn.contains(rn)) { //deshalb prÃ¼fen, ob die rechnung schon drin ist, bevor sie hinzugefÃ¼gt wird.
								rnn.add(rn);
							}
						}
					}
				}
			}
		}	}
	
	@Override
	protected Control createDialogArea(final Composite parent){
		Composite ret = new Composite(parent, SWT.NONE);
		ret.setLayout(new FillLayout());
		ret.setLayoutData(SWTHelper.getFillGridData(1, true, 1, true));
		
		//201512211432js: Siehe auch Rechnungsdrucker.java public class RechnungsDrucker.createSettingsControl()
		//TODO: Auf Konstante umstellen, dann braucht's allerdings den Austausch weiterer Module bei Installation!!!
		
		Group cSaveCopy = new Group(ret, SWT.NONE);
		cSaveCopy.setText(String.format(Messages.RnActions_exportSaveHelp, RnListExportFileName));
		cSaveCopy.setLayout(new GridLayout(2, false));
		Button bSelectFile = new Button(cSaveCopy, SWT.PUSH);
		bSelectFile.setText(Messages.RnActions_exportListDirName);
		bSelectFile.setLayoutData(SWTHelper.getFillGridData(2, false, 1, false));
		bSelectFile.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e){
				DirectoryDialog ddlg = new DirectoryDialog(parent.getShell());
				RnListExportDirname = ddlg.open();
				if (RnListExportDirname == null) {
					SWTHelper.alert(Messages.RnActions_exportListDirNameMissingCaption,
						Messages.RnActions_exportListDirNameMissingText);
				} else {
					//ToDo: Umstellen auf externe Konstante!
					CoreHub.localCfg.set("rechnung/RnListExportDirname", RnListExportDirname);
					tDirName.setText(RnListExportDirname);
				}
			}
		});
		tDirName = new Text(cSaveCopy, SWT.BORDER | SWT.READ_ONLY);
		tDirName.setText(CoreHub.localCfg.get("rechnung/RnListExportDirname", "")); //$NON-NLS-1$
		tDirName.setLayoutData(SWTHelper.getFillGridData(2, true, 1, false));
		return ret;
	}
	
	@Override
	public void create(){
		super.create();
		getShell().setText(Messages.RnActions_billsList);
		setTitle(Messages.RnActions_exportListCaption);
		setMessage(Messages.RnActions_exportListMessage);
		getShell().setSize(900, 700);
		SWTHelper.center(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
			getShell());
	}
	
	@Override
	protected void okPressed(){
		super.okPressed();
		if (CoreHub.localCfg.get("rechnung/RnListExportDirname_bSaveFileAs", true))
			CSVWriteTable();
	}
	
	public void save(){}
	
	public boolean saveAs(){
		return false;
	}
	
	public void CSVWriteTable(){
		String pathToSave = RnListExportDirname + "/" + RnListExportFileName;
		CSVWriter csv = null;
		int nrLines = 0;
		try {
			csv = new CSVWriter(new FileWriter(pathToSave));
			// @formatter:off
			String[] header = new String[] {
				"Aktion?", // line 0 
				"Re.Nr", // line 1
				"Re.DatumRn", // line 2
				"Re.DatumVon", // line 3
				"Re.DatumBis", // line 4
				"Re.Garant", // line 5
				"Re.Total", // line 6
				"Re.Offen", // line 7
				"Re.StatusLastUpdate", // line 8
				"Re.Status", // line 9
				"Re.StatusIsActive", // line 10
				"Re.StatusText", // line 11
				"Re.StatusChanges", // line 12
				"Re.Rejecteds", // line 13
				"Re.Outputs", // line 14
				"Re.Payments", // line 15
				"Fall.AbrSystem", // line 16
				"Fall.Bezeichnung", // line 17
				"Fall.Grund", // line 18
				"Pat.Nr", // line 10
				"Pat.Name", // line 20
				"Pat.Vorname", // line 21
				"Pat.GebDat", // line 22
				"Pat.LztKonsDat", // line 23
				"Pat.Balance", // line 24
				"Pat.GetAccountExcess", // line 25
				"Pat.BillSummary.Total.", // line 26
				"Pat.BillSummary.Paid", // line 27
				"Pat.BillSummary.Open" // line 28
			};
			// @formatter:on
			log.debug("csv export started for {} with {} fields for {} invoices", pathToSave, header.length, rnn.size());
			csv.writeNext(header);
			nrLines++;
			int i;
			for (i = 0; i < rnn.size(); i++) {
				Rechnung rn = rnn.get(i);
				Fall fall = rn.getFall();
				Patient p = fall.getPatient();
				String[] line = new String[header.length];
				line[0] = ""; //201512210402js: Leere Spalte zum Eintragen der gewÃ¼nschten Aktion.
				line[1] = rn.getNr();
				line[2] = rn.getDatumRn();
				line[3] = rn.getDatumVon();
				line[4] = rn.getDatumBis();
				line[5] = fall.getGarant().getLabel();
				line[6] = rn.getBetrag().toString();
				line[7] = rn.getOffenerBetrag().toString();
				long luTime = rn.getLastUpdate();
				Date date = new Date(luTime);
				// TODO: Support other date formats based upon location or configured settings
				SimpleDateFormat df2 = new SimpleDateFormat("dd.MM.yyyy");
				String dateText = df2.format(date);
				line[8] = dateText.toString();
				int st = rn.getStatus();
				line[9] = Integer.toString(st);
				if (RnStatus.isActive(st)) {
					line[10] = "True";
				} else {
					line[10] = "False";
				}
				line[11] = RnStatus.getStatusText(st);
				// 201512210310js: New: produce 4 fields, each with multiline content.
				List<String> statuschgs = rn.getTrace(Rechnung.STATUS_CHANGED);
				String a = statuschgs.toString();
				if (a != null && a.length() > 1) {
					//Die Uhrzeiten rauswerfen:
					a = a.replaceAll(", [0-9][0-9]:[0-9][0-9]:[0-9][0-9]", "");
					//", " durch "\n" ersetzen (Man kÃ¶nnte auch noch prÃ¼fen, ob danach eine Zahl/ein Datum kommt - die dann aber behalten werden muss.)
					a = a.replaceAll(", ", "\n");
					//FÃ¼hrende und Trailende [] bei der Ausgabe (!) rauswerfen
					line[12] = a.substring(1, a.length() - 1);
				}
				if (rn.getStatus() == RnStatus.FEHLERHAFT) {
					List<String> rejects = rn.getTrace(Rechnung.REJECTED);
					String rnStatus = rejects.toString();
					if (rnStatus != null && rnStatus.length() > 1) {
						//Die Uhrzeiten rauswerfen:
						rnStatus =
							rnStatus.replaceAll(", [0-9][0-9]:[0-9][0-9]:[0-9][0-9]", "");
						//", " durch "\n" ersetzen (Man kÃ¶nnte auch noch prÃ¼fen, ob danach eine Zahl/ein Datum kommt - die dann aber behalten werden muss.)
						rnStatus = rnStatus.replaceAll(", ", "\n");
						//FÃ¼hrende und Trailende [] bei der Ausgabe (!) rauswerfen
						line[13] = rnStatus.substring(1, rnStatus.length() - 1);
					}
				}
				List<String> outputs = rn.getTrace(Rechnung.OUTPUT);
				String rnOutput = outputs.toString();
				if (rnOutput != null && rnOutput.length() > 1) {
					//Die Uhrzeiten rauswerfen:
					rnOutput = rnOutput.replaceAll(", [0-9][0-9]:[0-9][0-9]:[0-9][0-9]", "");
					//", " durch "\n" ersetzen (Man kÃ¶nnte auch noch prÃ¼fen, ob danach eine Zahl/ein Datum kommt - die dann aber behalten werden muss.)
					rnOutput = rnOutput.replaceAll(", ", "\n");
					//FÃ¼hrende und Trailende [] bei der Ausgabe (!) rauswerfen
					line[14] = rnOutput.substring(1, rnOutput.length() - 1);
				}
				List<String> payments = rn.getTrace(Rechnung.PAYMENT);
				String rnPayment = payments.toString();
				if (rnPayment != null && rnPayment.length() > 1) {
					//Die Uhrzeiten rauswerfen:
					rnPayment = rnPayment.replaceAll(", [0-9][0-9]:[0-9][0-9]:[0-9][0-9]", "");
					//", " durch "\n" ersetzen (Man kÃ¶nnte auch noch prÃ¼fen, ob danach eine Zahl/ein Datum kommt - die dann aber behalten werden muss.)
					rnPayment = rnPayment.replaceAll(", ", "\n");
					//FÃ¼hrende und Trailende [] bei der Ausgabe (!) rauswerfen
					line[15] = rnPayment.substring(1, rnPayment.length() - 1);
				}
				// Jetzt alles zum betroffenen Fall:
				line[16] = fall.getAbrechnungsSystem();
				line[17] = fall.getBezeichnung();
				line[18] = fall.getGrund();
				// Jetzt alles zum betroffenen Patienten:
				line[19] = p.getKuerzel();
				line[20] = p.getName();
				line[21] = p.getVorname();
				line[22] = p.getGeburtsdatum();
				// TODO: allenfalls wieder: auf n.a. oder so setzen...
				// TODO: Ich mÃ¶cht aber wissen, ob p (dÃ¼rfte eigentlich nie der Fall sein) oder nk schuld sind, wenn nichts rauskommt.
				// TODO: Na ja, eigentlich wÃ¼rd ich noch lieber wissen, WARUM da manchmal nichts rauskommt, obwohl eine kons sicher vhd ist.
				String lkDatum = "p==null";
				if (p != null) {
					Konsultation lk = p.getLetzteKons(false);
					if (lk != null) {
						lkDatum = (lk.getDatum());
					} else {
						lkDatum = "lk==null";
					}
				}
				line[23] = lkDatum;
				line[24] = p.getBalance(); //returns: String
				line[25] = p.getAccountExcess().toString(); //returns: Money
				//201512210146js: Das Folgende ist aus BillSummary - dort wird dafÃ¼r keine Funktion bereitgestellt,
				// TODO: PrÃ¼fen, ob das eine Redundanz DORT und HIER ist vs. obenn erwÃ¤hnter getKontostand(), getAccountExcess() etc.
				// maybe called from foreign thread
				String totalText = ""; //$NON-NLS-1$
				String paidText = ""; //$NON-NLS-1$
				String openText = ""; //$NON-NLS-1$
				// Davon, dass p != null ist, darf man eigentlich ausgehen, da ja Rechnungen zu p gehÃ¶ren etc.
				if (p != null) {
					Money total = new Money(0);
					Money paid = new Money(0);
					List<Rechnung> rechnungen = p.getRechnungen();
					for (Rechnung rechnung : rechnungen) {
						// don't consider canceled bills
						if (rechnung.getStatus() != RnStatus.STORNIERT) {
							total.addMoney(rechnung.getBetrag());
							for (Zahlung zahlung : rechnung.getZahlungen()) {
								paid.addMoney(zahlung.getBetrag());
							}
						}
					}
					Money open = new Money(total);
					open.subtractMoney(paid);
					totalText = total.toString();
					paidText = paid.toString();
					openText = open.toString();
				}
				line[26] = totalText;
				line[27] = paidText;
				line[28] = openText;
				csv.writeNext(line);
				nrLines++;
			}
			csv.close();
			log.debug("{}: Wrote {} lines for {} invoices", pathToSave, nrLines, rnn.size());
		} catch (Exception ex) {
			ExHandler.handle(ex);
			log.error("csv exporter error", ex);
			SWTHelper.showError("Fehler", ex.getMessage());
		} finally {
			if (csv != null) {
				try {
					csv.close();
				} catch (IOException e) {
					log.error("cannot close csv exporter", e);
				}
			}
		}
	}
}
*/