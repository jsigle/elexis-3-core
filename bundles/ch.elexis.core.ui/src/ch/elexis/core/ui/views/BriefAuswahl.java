/*******************************************************************************
 * Copyright (c) 2006-2010, G. Weirich and Elexis
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    G. Weirich - initial implementation
 *    
 *******************************************************************************/

package ch.elexis.core.ui.views;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.NotEnabledException;
import org.eclipse.core.commands.NotHandledException;
import org.eclipse.core.commands.common.NotDefinedException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.ISaveablePart2;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.forms.widgets.Form;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.part.ViewPart;

import ch.elexis.core.constants.Preferences;
import ch.elexis.core.data.activator.CoreHub;
import ch.elexis.core.data.events.ElexisEvent;
import ch.elexis.core.data.events.ElexisEventDispatcher;
import ch.elexis.core.data.events.ElexisEventListener;
import ch.elexis.core.ui.UiDesk;
import ch.elexis.core.ui.actions.GlobalActions;
import ch.elexis.core.ui.actions.GlobalEventDispatcher;
import ch.elexis.core.ui.actions.IActivationListener;
import ch.elexis.core.ui.dialogs.DocumentSelectDialog;
import ch.elexis.core.ui.dialogs.SelectFallDialog;
import ch.elexis.core.ui.events.ElexisUiEventListenerImpl;
import ch.elexis.core.ui.icons.Images;
import ch.elexis.core.ui.locks.LockRequestingAction;
import ch.elexis.core.ui.services.LocalDocumentServiceHolder;
import ch.elexis.core.ui.util.SWTHelper;
import ch.elexis.core.ui.util.ViewMenus;
import ch.elexis.core.ui.util.viewers.CommonViewer;
import ch.elexis.core.ui.util.viewers.CommonViewer.DoubleClickListener;
import ch.elexis.core.ui.util.viewers.DefaultContentProvider;
import ch.elexis.core.ui.util.viewers.DefaultControlFieldProvider;
import ch.elexis.core.ui.util.viewers.DefaultLabelProvider;
import ch.elexis.core.ui.util.viewers.SimpleWidgetProvider;
import ch.elexis.core.ui.util.viewers.ViewerConfigurer;
import ch.elexis.data.Brief;
import ch.elexis.data.Fall;
import ch.elexis.data.Konsultation;
import ch.elexis.data.Kontakt;
import ch.elexis.data.Patient;
import ch.elexis.data.PersistentObject;
import ch.elexis.data.Query;
import ch.rgw.tools.ExHandler;
import ch.rgw.tools.TimeTool;

public class BriefAuswahl extends ViewPart implements
		ch.elexis.core.data.events.ElexisEventListener, IActivationListener, ISaveablePart2 {
	
	public final static String ID = "ch.elexis.BriefAuswahlView"; //$NON-NLS-1$
	private final FormToolkit tk;
	private Form form;
	private Action briefNeuAction, briefLadenAction, editNameAction, startLocalEditAction,
			endLocalEditAction, cancelLocalEditAction;
	private Action deleteAction;
	private Action stressTest1Action, stressTest2Action;	//20140421js: added stress test feature.	
	private ViewMenus menus;
	private ArrayList<sPage> pages = new ArrayList<sPage>();
	CTabFolder ctab;
	private ElexisEventListener updateListener =
		new ElexisUiEventListenerImpl(Brief.class, ElexisEvent.EVENT_RELOAD) {
			@Override
			public void runInUi(ElexisEvent ev){
				relabel();
			}
	};

	// private ViewMenus menu;
	// private IAction delBriefAction;
	public BriefAuswahl(){
		tk = UiDesk.getToolkit();
	}
	
	@Override
	public void createPartControl(final Composite parent){
		StringBuilder sb = new StringBuilder();
		sb.append(Messages.BriefAuswahlAllLetters).append(Brief.UNKNOWN).append(",") //$NON-NLS-1$
			.append(Brief.AUZ).append(",").append(Brief.RP).append(",").append(Brief.LABOR); //$NON-NLS-1$ //$NON-NLS-2$
		String cats = CoreHub.globalCfg.get(Preferences.DOC_CATEGORY, sb.toString());
		parent.setLayout(new GridLayout());
		
		form = tk.createForm(parent);
		form.setLayoutData(SWTHelper.getFillGridData(1, true, 1, true));
		form.setBackground(parent.getBackground());
		
		// Grid layout with zero margins
		GridLayout slimLayout = new GridLayout();
		slimLayout.marginHeight = 0;
		slimLayout.marginWidth = 0;
		
		Composite body = form.getBody();
		body.setLayout(slimLayout);
		body.setBackground(parent.getBackground());
		
		ctab = new CTabFolder(body, SWT.BOTTOM);
		ctab.setLayout(slimLayout);
		ctab.setLayoutData(SWTHelper.getFillGridData(1, true, 1, true));
		ctab.setBackground(parent.getBackground());
		makeActions();
		menus = new ViewMenus(getViewSite());
		
		for (String cat : cats.split(",")) { //$NON-NLS-1$
			CTabItem ct = new CTabItem(ctab, SWT.NONE);
			ct.setText(cat);
			sPage page = new sPage(ctab, cat);
			pages.add(page);
			if (CoreHub.localCfg.get(Preferences.P_TEXT_EDIT_LOCAL, false)) {
				menus.createViewerContextMenu(page.cv.getViewerWidget(), editNameAction,
					deleteAction, startLocalEditAction, endLocalEditAction, cancelLocalEditAction);
			} else {
				menus.createViewerContextMenu(page.cv.getViewerWidget(), editNameAction,
					deleteAction);
			}
			ct.setData(page.cv);
			ct.setControl(page);
			page.cv.addDoubleClickListener(new DoubleClickListener() {
				@Override
				public void doubleClicked(PersistentObject obj, CommonViewer cv){
					briefLadenAction.run();
				}
			});
		}
		
		ctab.addSelectionListener(new SelectionAdapter() {
			
			@Override
			public void widgetSelected(final SelectionEvent e){
				relabel();
			}
			
		});
		
		GlobalEventDispatcher.addActivationListener(this, this);
		menus.createMenu(briefNeuAction, briefLadenAction, editNameAction, deleteAction,
				stressTest1Action, stressTest2Action		//20140421js: added stress test feature.
				);
		menus.createToolbar(briefNeuAction, briefLadenAction, deleteAction);
		ctab.setSelection(0);
		relabel();
	}
	
	@Override
	public void dispose(){
		ElexisEventDispatcher.getInstance().removeListeners(this);
		GlobalEventDispatcher.removeActivationListener(this, this);
		
		for (sPage page : pages) {
			page.getCommonViewer().getConfigurer().getContentProvider().stopListening();
		}
	}
	
	@Override
	public void setFocus(){
		
	}
	
	public void relabel(){
		UiDesk.asyncExec(new Runnable() {
			public void run(){
				Patient pat = (Patient) ElexisEventDispatcher.getSelected(Patient.class);
				if (form != null && !form.isDisposed()) {
					if (pat == null) {
						form.setText(Messages.BriefAuswahlNoPatientSelected); //$NON-NLS-1$
					} else {
						form.setText(pat.getLabel());
						CTabItem sel = ctab.getSelection();
						if (sel != null) {
							CommonViewer cv = (CommonViewer) sel.getData();
							cv.notify(CommonViewer.Message.update);
						}
					}
				}
			}
		});
		
	}
	
	class sPage extends Composite {
		private TableViewer tableViewer;
		private LetterViewerComparator comparator;
		private final CommonViewer cv;
		private final ViewerConfigurer vc;
		
		public CommonViewer getCommonViewer(){
			return cv;
		}
		
		sPage(final Composite parent, final String cat){
			super(parent, SWT.NONE);
			setLayout(new GridLayout());
			cv = new CommonViewer();
			vc = new ViewerConfigurer(new DefaultContentProvider(cv, Brief.class, new String[] {
				Brief.FLD_DATE
			}, true) {
				
				@Override
				public Object[] getElements(final Object inputElement){
					Patient actPat = (Patient) ElexisEventDispatcher.getSelected(Patient.class);
					if (actPat != null) {
						Query<Brief> qbe = new Query<Brief>(Brief.class);
						qbe.add(Brief.FLD_PATIENT_ID, Query.EQUALS, actPat.getId());
						if (cat.equals(Messages.BriefAuswahlAllLetters2)) { //$NON-NLS-1$
							qbe.add(Brief.FLD_TYPE, Query.NOT_EQUAL, Brief.TEMPLATE);
						} else {
							qbe.add(Brief.FLD_TYPE, Query.EQUALS, cat);
						}
						cv.getConfigurer().getControlFieldProvider().setQuery(qbe);
						
						List<Brief> list = qbe.execute();
						return list.toArray();
					} else {
						return new Brief[0];
					}
				}
				
			}, new DefaultLabelProvider(), new DefaultControlFieldProvider(cv, new String[] {
				"Betreff=Titel" //$NON-NLS-1$
			}), new ViewerConfigurer.DefaultButtonProvider(), new SimpleWidgetProvider(
				SimpleWidgetProvider.TYPE_TABLE, SWT.V_SCROLL | SWT.FULL_SELECTION, cv));
			cv.create(vc, this, SWT.NONE, getViewSite());
			
			tableViewer = (TableViewer) cv.getViewerWidget();
			tableViewer.getTable().setHeaderVisible(true);
			createColumns();
			comparator = new LetterViewerComparator();
			tableViewer.setComparator(comparator);
			if (CoreHub.localCfg.get(Preferences.P_TEXT_RENAME_WITH_F2, false)) {
				tableViewer.getTable().addKeyListener(new KeyListener() {
					@Override
					public void keyPressed(KeyEvent e){}
					
					@Override
					public void keyReleased(KeyEvent e){
						if (e.keyCode == SWT.F2) {
							editNameAction.run();
						}
					}
				});
			}
			
			vc.getContentProvider().startListening();
			Button bLoad = tk.createButton(this, Messages.BriefAuswahlLoadButtonText, SWT.PUSH); //$NON-NLS-1$
			bLoad.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(final SelectionEvent e){
					briefLadenAction.run();
				}
				
			});
			bLoad.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
		}
		
		// create the columns for the table
		private void createColumns(){
			// first column - date
			TableViewerColumn col = new TableViewerColumn(tableViewer, SWT.NONE);
			col.getColumn().setText(Messages.BriefAuswahlColumnDate);
			col.getColumn().setWidth(100);
			col.getColumn().addSelectionListener(getSelectionAdapter(col.getColumn(), 0));
			col.setLabelProvider(new ColumnLabelProvider() {
				@Override
				public String getText(Object element){
					Brief b = (Brief) element;
					return b.getDatum();
				}
			});
			
			// second column - title
			col = new TableViewerColumn(tableViewer, SWT.NONE);
			col.getColumn().setText(Messages.BriefAuswahlColumnTitle);
			col.getColumn().setWidth(300);
			col.getColumn().addSelectionListener(getSelectionAdapter(col.getColumn(), 1));
			col.setLabelProvider(new ColumnLabelProvider() {
				@Override
				public String getText(Object element){
					Brief b = (Brief) element;
					return b.getBetreff();
				}
				
				@Override
				public Image getImage(Object element){
					if (LocalDocumentServiceHolder.getService().isPresent()) {
						if (LocalDocumentServiceHolder.getService().get().contains(element)) {
							return Images.IMG_EDIT.getImage();
						}
					}
					return super.getImage(element);
				}
			});
		}
		
		private SelectionAdapter getSelectionAdapter(final TableColumn column, final int index){
			SelectionAdapter selectionAdapter = new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e){
					comparator.setColumn(index);
					tableViewer.getTable().setSortDirection(comparator.getDirection());
					tableViewer.getTable().setSortColumn(column);
					tableViewer.refresh();
				}
			};
			return selectionAdapter;
		}
		
		class LetterViewerComparator extends ViewerComparator {
			private int propertyIndex;
			private boolean direction = true;
			private TimeTool time1;
			private TimeTool time2;
			
			public LetterViewerComparator(){
				this.propertyIndex = 0;
				time1 = new TimeTool();
				time2 = new TimeTool();
			}
			
			/**
			 * for sort direction
			 * 
			 * @return SWT.DOWN or SWT.UP
			 */
			public int getDirection(){
				return direction ? SWT.DOWN : SWT.UP;
			}
			
			public void setColumn(int column){
				if (column == this.propertyIndex) {
					// Same column as last sort; toggle the direction
					direction = !direction;
				} else {
					// New column; do an ascending sort
					this.propertyIndex = column;
					direction = true;
				}
			}
			
			@Override
			public int compare(Viewer viewer, Object e1, Object e2){
				if (e1 instanceof Brief && e2 instanceof Brief) {
					Brief b1 = (Brief) e1;
					Brief b2 = (Brief) e2;
					int rc = 0;
					switch (propertyIndex) {
					case 0:
						time1.set((b1).getDatum());
						time2.set((b2).getDatum());
						rc = time1.compareTo(time2);
						break;
					case 1:
						rc = b1.getBetreff().compareTo(b2.getBetreff());
						break;
					default:
						rc = 0;
					}
					// If descending order, flip the direction
					if (direction) {
						rc = -rc;
					}
					return rc;
				}
				return 0;
			}
		}
	}
	
	private void makeActions(){
		briefNeuAction = new Action(Messages.BriefAuswahlNewButtonText) { //$NON-NLS-1$
			@Override
			public void run(){
				Patient pat = ElexisEventDispatcher.getSelectedPatient();
				if (pat == null) {
					MessageDialog.openInformation(UiDesk.getTopShell(),
						Messages.BriefAuswahlNoPatientSelected,
						Messages.BriefAuswahlNoPatientSelected);
					return;
				}
				
				Fall selectedFall = (Fall) ElexisEventDispatcher.getSelected(Fall.class);
				if (selectedFall == null) {
					SelectFallDialog sfd = new SelectFallDialog(UiDesk.getTopShell());
					sfd.open();
					if (sfd.result != null) {
						ElexisEventDispatcher.fireSelectionEvent(sfd.result);
					} else {
						MessageDialog.openInformation(UiDesk.getTopShell(),
							Messages.TextView_NoCaseSelected, //$NON-NLS-1$
							Messages.TextView_SaveNotPossibleNoCaseAndKonsSelected); //$NON-NLS-1$
						return;
					}
				}
				
				Konsultation selectedKonsultation =
					(Konsultation) ElexisEventDispatcher.getSelected(Konsultation.class);
				if (selectedKonsultation == null) {
					Konsultation k = pat.getLetzteKons(false);
					if (k == null) {
						k = ((Fall) ElexisEventDispatcher.getSelected(Fall.class))
							.neueKonsultation();
						k.setMandant(CoreHub.actMandant);
					}
					ElexisEventDispatcher.fireSelectionEvent(k);
				}
				
				TextView tv = null;
				try {
					DocumentSelectDialog bs = new DocumentSelectDialog(getViewSite().getShell(),
						CoreHub.actMandant, DocumentSelectDialog.TYPE_CREATE_DOC_WITH_TEMPLATE);
					if (bs.open() == Dialog.OK) {
						tv = (TextView) getSite().getPage().showView(TextView.ID);
						// trick: just supply a dummy address for creating the doc
						Kontakt address = null;
						if (DocumentSelectDialog
							.getDontAskForAddresseeForThisTemplate(bs.getSelectedDocument()))
							address = Kontakt.load("-1"); //$NON-NLS-1$
						tv.createDocument(bs.getSelectedDocument(), bs.getBetreff(), address);
						tv.setName();
						CTabItem sel = ctab.getSelection();
						if (sel != null) {
							CommonViewer cv = (CommonViewer) sel.getData();
							cv.notify(CommonViewer.Message.update_keeplabels);
						}
						
					}
				} catch (Exception ex) {
					ExHandler.handle(ex);
				}
			}
		};
		
		briefLadenAction = new Action(Messages.BriefAuswahlOpenButtonText) { //$NON-NLS-1$
			@Override
			public void run(){
				try {
					CTabItem sel = ctab.getSelection();
					if (sel != null) {
						CommonViewer cv = (CommonViewer) sel.getData();
						Object[] o = cv.getSelection();
						if ((o != null) && (o.length > 0)) {
							Brief brief = (Brief) o[0];
							if (CoreHub.localCfg.get(Preferences.P_TEXT_EDIT_LOCAL, false)) {
								startLocalEditAction.run();
							} else {
								TextView tv =
									(TextView) getViewSite().getPage().showView(TextView.ID);
								if (brief.getMimeType().equalsIgnoreCase("pdf")) { //$NON-NLS-1$
									try {
										File temp = File.createTempFile("letter_", ".pdf"); //$NON-NLS-1$ //$NON-NLS-2$
										temp.deleteOnExit();
										try (FileOutputStream fos = new FileOutputStream(temp)) {
											fos.write(brief.loadBinary());
										}
										Program.launch(temp.getAbsolutePath());
									} catch (IOException e) {
										ExHandler.handle(e);
										SWTHelper.alert(Messages.BriefAuswahlErrorHeading, //$NON-NLS-1$
											Messages.BriefAuswahlCouldNotLoadText); //$NON-NLS-1$
									}
								} else if (tv.openDocument(brief) == false) {
									SWTHelper.alert(Messages.BriefAuswahlErrorHeading, //$NON-NLS-1$
										Messages.BriefAuswahlCouldNotLoadText); //$NON-NLS-1$
								}
							}
						} else {
							TextView tv = (TextView) getViewSite().getPage().showView(TextView.ID);
							tv.createDocument(null, null);
						}
						cv.notify(CommonViewer.Message.update);
					}
				} catch (PartInitException e) {
					ExHandler.handle(e);
				}
				
			}
		};
		
		//20210329js: restored this feature from Elexis 2.1.7js to Elexis 3.7js
		//20140421js: added stress test feature.
		//It is important that the stressTest calls the text interface as exactly as possible
		//in the same way as when a document ist opened from a real view - because we want to
		//get truly representative results regarding stack usage / memory leaks / ability to
		//run concurrently and independently from multiple instances of Elexis without interference.
		//Therefore, the stressTest shall remain as a function in Briefauswahl and NOT be put
		//into another module (which would imply additional calling/returning overhead etc.).
		//To protect users from unwanting/unknowing usage - and thereby blocking their system
		//for a few minutes - for now, I add a confirmation dialog before the stress test starts.
		//Later on, we may display the respective menu entries only for Administrators or (better)
		//only, when a respective checkbox in the settings for the text-interface is checked.
		//But that's more than I want to do today. 
		stressTest1Action= new Action(Messages.BriefAuswahlStressTestButtonText1) { //$NON-NLS-1$
			@Override
			public void run(){
				Integer plannedNumberOfPasses = 100;				
				//Ask for confirmation before running the StressTest
				if (! SWTHelper.askYesNo(Messages.BriefAuswahlStressTestButtonText1, //$NON-NLS-1$
						Messages.BriefAuswahlStresstestAskForConfirmationBeforeRunning
						+ " n = " + plannedNumberOfPasses.toString())) {
					return;
				}
				
				System.out.println();
				System.out.println("****************************************************************");
				System.out.println("js ch.elexis.views/BriefAuswahl.java: Initiating stress test 1.");
				System.out.println("****************************************************************");
				System.out.println();
				System.out.println("This stress test will open the selected document repeatedly until you close the program or an error occurs.");
				System.out.println();
				int stressTestPasses=0;
				Boolean continueStressTest=true;
				while (continueStressTest) {
				
				stressTestPasses=stressTestPasses+1;
				System.out.println("stress test pass: "+stressTestPasses+" / "+plannedNumberOfPasses+" - about to load document...");

				try {
					TextView tv = (TextView) getViewSite().getPage().showView(TextView.ID);
					CTabItem sel = ctab.getSelection();
					if (sel != null) {
						System.out.println("stress test pass: "+stressTestPasses+" - sel != null; sel.getText()=<"+sel.getText().toString()+">");
						CommonViewer cv = (CommonViewer) sel.getData();
						Object[] o = cv.getSelection();
						if ((o != null) && (o.length > 0)) {
							Brief brief = (Brief) o[0];
							System.out.println("stress test pass: "+stressTestPasses+" - o !!= null; (Brief) o[0.getLabel()]=<"+brief.getLabel().toString()+">");
							System.out.println("stress test pass: "+stressTestPasses+" - try {} section o != null; about to tv.openDocument(brief)....");
							if (tv.openDocument(brief) == false) {
								System.out.println("stress test pass: "+stressTestPasses+" - try {} section tv.openDocument(brief) returned false. Setting continueStressTest=false.");
								continueStressTest=false;
								SWTHelper.alert(Messages.BriefAuswahlErrorHeading, //$NON-NLS-1$
									Messages.BriefAuswahlCouldNotLoadText); //$NON-NLS-1$
							}
							else {
								System.out.println("stress test pass: "+stressTestPasses+" - try {} section tv.openDocument(brief) worked; document should have been loaded.");
							}
						} else {
							System.out.println("stress test pass: "+stressTestPasses+" - try {} section o == null; about to tv.createDocument(null,null). Setting continueStressTest=false.");
							continueStressTest=false;
							tv.createDocument(null, null);
						}
						System.out.println("stress test pass: "+stressTestPasses+" - try {} section; about to cv.notify(CommonViewer.Message.update);...");
						cv.notify(CommonViewer.Message.update);
						System.out.println("stress test pass: "+stressTestPasses+" - try {} section completed.");
					}
				} catch (PartInitException e) {
					System.out.println("stress test pass: "+stressTestPasses+" - catch {} section handling exception. Setting continueStressTest=false.");
					continueStressTest=false;
					ExHandler.handle(e);
					System.out.println("stress test pass: "+stressTestPasses+" - catch {} section completed.");
				}
				System.out.println("stress test pass: "+stressTestPasses+" - try/catch completed.");

				if (stressTestPasses>plannedNumberOfPasses) {
					System.out.println("stress test pass: "+stressTestPasses+" - Setting continueStressTest=false after "+stressTestPasses+" passes have completed.");						
					continueStressTest=false;
				}

				try {
					System.out.println("stress test pass: "+stressTestPasses+" - about to Thread.sleep()...(Otherwise the Briefe view content would not be visibly updated.)");
					Thread.sleep(1000);
				} catch (Throwable throwable) {
					//handle the interrupt that will happen after the sleep 
					System.out.println("stress test pass: "+stressTestPasses+" - caught throwable; most probably the Thread.sleep() wakeup interrupt signal.");
				}
				
				System.out.println("****************************************************************");				
			
			}	//while true for stress test js
			System.out.println("stress test pass: "+stressTestPasses+" - stress test ends.");
				
			}
		};

		//20210329js: restored this feature from Elexis 2.1.7js to Elexis 3.7js
		//20140421js: added stress test feature.
		//It is important that the stressTest calls the text interface as exactly as possible
		//in the same way as when a document ist opened from a real view - because we want to
		//get truly representative results regarding stack usage / memory leaks / ability to
		//run concurrently and independently from multiple instances of Elexis without interference.
		//Therefore, the stressTest shall remain as a function in Briefauswahl and NOT be put
		//into another module (which would imply additional calling/returning overhead etc.).
		//To protect users from unwanting/unknowing usage - and thereby blocking their system
		//for a few minutes - for now, I add a confirmation dialog before the stress test starts.
		//Later on, we may display the respective menu entries only for Administrators or (better)
		//only, when a respective checkbox in the settings for the text-interface is checked.
		//But that's more than I want to do today. 
		stressTest2Action = new Action(Messages.BriefAuswahlStressTestButtonText2) { //$NON-NLS-1$
			@Override
			public void run(){
				Integer plannedNumberOfPasses = 100;				
				//Ask for confirmation before running the StressTest
				if (! SWTHelper.askYesNo(Messages.BriefAuswahlStressTestButtonText1,
						Messages.BriefAuswahlStresstestAskForConfirmationBeforeRunning
						+ " n = " + plannedNumberOfPasses.toString())) {
					return;
				}

				System.out.println();
				System.out.println("****************************************************************");
				System.out.println("js ch.elexis.views/BriefAuswahl.java: Initiating stress test 2.");
				System.out.println("****************************************************************");
				System.out.println();
				System.out.println("This stress test will open all Briefe of the selected patient one after another, repeatedly, until you close the program or an error occurs.");
				System.out.println();
				
				int stressTestPasses=0;
				Boolean continueStressTest=true;
				
				//obtain a list of all documents for the current patient
				Patient actPat = (Patient) ElexisEventDispatcher.getSelected(Patient.class);
				if (actPat != null) {
					Query<Brief> qbe = new Query<Brief>(Brief.class);
					qbe.add(Brief.FLD_PATIENT_ID, Query.EQUALS, actPat.getId());
					qbe.add(Brief.FLD_TYPE, Query.NOT_EQUAL, Brief.TEMPLATE);
								
					List<Brief> list = qbe.execute();
					//list.toArray()
					System.out.println("Liste der Briefe des Patienten: "+list);
				
					//das noch hinzugefügt nach erster Fassung, die archiviert wurde...
					while (continueStressTest) {
						
						//open one document after annother; each adds another pass to the stress test pass count
						for (Brief brief : list) {

							if ( brief != null ) {
								
								stressTestPasses=stressTestPasses+1;
								System.out.println("stress test pass: "+stressTestPasses+" / "+plannedNumberOfPasses+" - about to load document...");
							
								try {
									TextView tv = (TextView) getViewSite().getPage().showView(TextView.ID);

									System.out.println("stress test pass: "+stressTestPasses+" - o !!= null; (Brief) brief[0.getLabel()]=<"+brief.getLabel().toString()+">");
									System.out.println("stress test pass: "+stressTestPasses+" - try {} section o != null; about to tv.openDocument(brief)....");
																
									if (tv.openDocument(brief) == false) {
										System.out.println("stress test pass: "+stressTestPasses+" - try {} section tv.openDocument(brief) returned false. Setting continueStressTest=false.");
										SWTHelper.alert(Messages.BriefAuswahlErrorHeading, //$NON-NLS-1$
													Messages.BriefAuswahlCouldNotLoadText); //$NON-NLS-1$
										continueStressTest=false;
										break;
									}	else {
										
										//Das ist jedenfalls kontraindiziert: Wirft eine unhandled exception, weil der Thread ja nicht darauf gewartet hat:
										//tv.notify();
										//Die folgenden verbessern nichts am Verhalten: Die ersten wenigen Dokumente  werden aktualisiert angezeigt, danach keines ausser dem letzten:
										//tv.txt.setFocus();
										
										//tv.textContainer.update();
										
										//tv.textContainer.redraw();

										//tv.textContainer.update();
										//tv.textContainer.redraw();
										
										//tv.textContainer.redraw();
										//tv.textContainer.update();
										
										/*
										while (tv.getViewSite()==null ) {
											System.out.println("stress test pass: "+stressTestPasses+" - try {} section waiting for view to complete initialization...");

											try {
												System.out.println("stress test pass: "+stressTestPasses+" - about to Thread.sleep(10)...");
												Thread.sleep(10);
											} catch (Throwable throwable) {
												//handle the interrupt that will happen after the sleep 
												System.out.println("stress test pass: "+stressTestPasses+" - caught throwable; most probably the Thread.sleep() wakeup interrupt signal.");
											}
										}
										*/

										//tv.dispose();
										
										System.out.println("stress test pass: "+stressTestPasses+" - try {} section tv.openDocument(brief) worked; document should have been loaded.");
										}
								} catch (PartInitException e) {
									System.out.println("stress test pass: "+stressTestPasses+" - catch {} section handling exception. Setting continueStressTest=false.");
									ExHandler.handle(e);
									System.out.println("stress test pass: "+stressTestPasses+" - catch {} section completed.");
									continueStressTest=false;
									break;
								}
								System.out.println("stress test pass: "+stressTestPasses+" - try/catch completed.");
	
								if (stressTestPasses>plannedNumberOfPasses) {
									System.out.println("stress test pass: "+stressTestPasses+" - Setting continueStressTest=false after "+stressTestPasses+" passes have completed.");						
									continueStressTest=false;
									break;
								}
								
								
								try {
									System.out.println("stress test pass: "+stressTestPasses+" - about to Thread.sleep()...(Otherwise the Briefe view content would not be visibly updated.)");
									//Nichts von den folgenden hilft tatsächlich gut gegen das mangelnde Updaten im LibreOffice Frame nach dem ca. 4. Dokument:
									//Thread.sleep(10000);
									//Thread.sleep(1000);
									//Thread.yield();
								} catch (Throwable throwable) {
									//handle the interrupt that will happen after the sleep 
									System.out.println("stress test pass: "+stressTestPasses+" - caught throwable; most probably the Thread.sleep() wakeup interrupt signal.");
								}
								
							
								System.out.println("****************************************************************");

							} //if ( brief != null)
							
						} //for ( brief : list )
					} //while (continueStressTest)
				} //if (actPat != null )
			System.out.println("stress test pass: "+stressTestPasses+" - stress test ends.");
				
			}
		};
		
		deleteAction = new LockRequestingAction<Brief>(Messages.BriefAuswahlDeleteButtonText) { //$NON-NLS-1$
			@Override
			public void doRun(Brief brief){
				if (brief != null && SWTHelper.askYesNo(Messages.BriefAuswahlDeleteConfirmHeading, //$NON-NLS-1$
					Messages.BriefAuswahlDeleteConfirmText)) {
					brief.delete();
					CTabItem sel = ctab.getSelection();
					CommonViewer cv = (CommonViewer) sel.getData();
					cv.notify(CommonViewer.Message.update);
				}
			}
			
			@Override
			public Brief getTargetedObject(){
				CTabItem sel = ctab.getSelection();
				if ((sel != null)) { //$NON-NLS-1$
					CommonViewer cv = (CommonViewer) sel.getData();
					Object[] o = cv.getSelection();
					if ((o != null) && (o.length > 0)) {
						if (o[0] instanceof Brief) {
							return (Brief) o[0];
						}
					}
				}
				return null;
			}
		};
		editNameAction = new LockRequestingAction<Brief>(Messages.BriefAuswahlRenameButtonText) { //$NON-NLS-1$
			@Override
			public void doRun(Brief brief){
				if (brief != null) {
					InputDialog id = new InputDialog(getViewSite().getShell(),
						Messages.BriefAuswahlNewSubjectHeading, //$NON-NLS-1$
						Messages.BriefAuswahlNewSubjectText, //$NON-NLS-1$
						brief.getBetreff(), null);
					if (id.open() == Dialog.OK) {
						brief.setBetreff(id.getValue());
					}
					CTabItem sel = ctab.getSelection();
					CommonViewer cv = (CommonViewer) sel.getData();
					cv.notify(CommonViewer.Message.update);
				}
			}
			
			@Override
			public Brief getTargetedObject(){
				CTabItem sel = ctab.getSelection();
				if (sel != null) { //$NON-NLS-1$
					CommonViewer cv = (CommonViewer) sel.getData();
					Object[] o = cv.getSelection();
					if ((o != null) && (o.length > 0)) {
						if (o[0] instanceof Brief) {
							return (Brief) o[0];
						}
					}
				}
				return null;
			}
		};
		startLocalEditAction = new Action() {
			@Override
			public ImageDescriptor getImageDescriptor(){
				return Images.IMG_EDIT.getImageDescriptor();
			}
			
			@Override
			public String getText(){
				return Messages.BriefAuswahl_actionlocaledittext;
			}
			
			@Override
			public void run(){
				Brief brief = getSelectedBrief();
				if (brief != null) {
					ICommandService commandService = (ICommandService) PlatformUI.getWorkbench()
						.getService(ICommandService.class);
					Command command = commandService
						.getCommand("ch.elexis.core.ui.command.startEditLocalDocument"); //$NON-NLS-1$
					PlatformUI.getWorkbench().getService(IEclipseContext.class)
						.set(command.getId().concat(".selection"), new StructuredSelection(brief));
					try {
						command.executeWithChecks(new ExecutionEvent(command, Collections.EMPTY_MAP,
							this, null));
					} catch (ExecutionException | NotDefinedException | NotEnabledException
							| NotHandledException e) {
						MessageDialog.openError(getSite().getShell(), Messages.BriefAuswahl_errorttile,
							Messages.BriefAuswahl_erroreditmessage);
					}
					refreshSelectedViewer();
				}
			}
		};
		endLocalEditAction = new Action() {
			@Override
			public ImageDescriptor getImageDescriptor(){
				return Images.IMG_EDIT_DONE.getImageDescriptor();
			}
			
			@Override
			public String getText(){
				return Messages.BriefAuswahl_actionlocaleditstopmessage;
			}
			
			@Override
			public void run(){
				Brief brief = getSelectedBrief();
				if (brief != null) {
					ICommandService commandService = (ICommandService) PlatformUI.getWorkbench().getService(ICommandService.class);
					Command command =
						commandService.getCommand("ch.elexis.core.ui.command.endLocalDocument"); //$NON-NLS-1$
					
					PlatformUI.getWorkbench().getService(IEclipseContext.class)
						.set(command.getId().concat(".selection"), new StructuredSelection(brief));
					try {
						command.executeWithChecks(
							new ExecutionEvent(command, Collections.EMPTY_MAP, this, null));
					} catch (ExecutionException | NotDefinedException | NotEnabledException
							| NotHandledException e) {
						MessageDialog.openError(getSite().getShell(), Messages.BriefAuswahl_errortitle,
							Messages.BriefAuswahl_errorlocaleditendmessage);
					}
				}
				refreshSelectedViewer();
			}
		};
		cancelLocalEditAction = new Action() {
			@Override
			public ImageDescriptor getImageDescriptor(){
				return Images.IMG_EDIT_ABORT.getImageDescriptor();
			}
			
			@Override
			public String getText(){
				return Messages.BriefAuswahl_actionlocaleditabortmessage;
			}
			
			@Override
			public void run(){
				Brief brief = getSelectedBrief();
				if (brief != null) {
					ICommandService commandService = (ICommandService) PlatformUI.getWorkbench()
						.getService(ICommandService.class);
					Command command =
						commandService.getCommand("ch.elexis.core.ui.command.abortLocalDocument"); //$NON-NLS-1$
					
					PlatformUI.getWorkbench().getService(IEclipseContext.class)
						.set(command.getId().concat(".selection"), new StructuredSelection(brief));
					try {
						command.executeWithChecks(new ExecutionEvent(command, Collections.EMPTY_MAP,
							this, null));
					} catch (ExecutionException | NotDefinedException | NotEnabledException
							| NotHandledException e) {
						MessageDialog.openError(getSite().getShell(), Messages.BriefAuswahl_errortitle,
							Messages.BriefAuswahl_errorlocaleditabortmessage);
					}
				}
				refreshSelectedViewer();
			}
		};
		/*
		 * importAction=new Action("Importieren..."){ public void run(){
		 * 
		 * } };
		 */
		briefLadenAction.setImageDescriptor(Images.IMG_DOCUMENT_TEXT.getImageDescriptor());
		briefLadenAction.setToolTipText(Messages.BriefAuswahlOpenLetterForEdit); //$NON-NLS-1$
		briefNeuAction.setImageDescriptor(Images.IMG_DOCUMENT_ADD.getImageDescriptor());
		briefNeuAction.setToolTipText(Messages.BriefAuswahlCreateNewDocument); //$NON-NLS-1$
		editNameAction.setImageDescriptor(Images.IMG_DOCUMENT_WRITE.getImageDescriptor());
		editNameAction.setToolTipText(Messages.BriefAuswahlRenameDocument); //$NON-NLS-1$
		deleteAction.setImageDescriptor(Images.IMG_DOCUMENT_REMOVE.getImageDescriptor());
		deleteAction.setToolTipText(Messages.BriefAuswahlDeleteDocument); //$NON-NLS-1$
	}
	
	public Brief getSelectedBrief(){
		CTabItem sel = ctab.getSelection();
		if ((sel != null)) {
			CommonViewer cv = (CommonViewer) sel.getData();
			Object[] o = cv.getSelection();
			if ((o != null) && (o.length > 0)) {
				if (o[0] instanceof Brief) {
					return (Brief) o[0];
				}
			}
		}
		return null;
	}
	
	public void refreshSelectedViewer(){
		CTabItem sel = ctab.getSelection();
		if ((sel != null)) {
			CommonViewer cv = (CommonViewer) sel.getData();
			cv.notify(CommonViewer.Message.update);
		}
	}
	
	public void activation(final boolean mode){
		// TODO Auto-generated method stub
		
	}
	
	public void visible(final boolean mode){
		if (mode == true) {
			ElexisEventDispatcher.getInstance().addListeners(this);
			ElexisEventDispatcher.getInstance().addListeners(updateListener);
			relabel();
		} else {
			ElexisEventDispatcher.getInstance().removeListeners(this);
			ElexisEventDispatcher.getInstance().removeListeners(updateListener);
		}
	}
	
	/*
	 * Die folgenden 6 Methoden implementieren das Interface ISaveablePart2 Wir benÃ¶tigen das
	 * Interface nur, um das Schliessen einer View zu verhindern, wenn die Perspektive fixiert ist.
	 * Gibt es da keine einfachere Methode?
	 */
	public int promptToSaveOnClose(){
		return GlobalActions.fixLayoutAction.isChecked() ? ISaveablePart2.CANCEL
				: ISaveablePart2.NO;
	}
	
	public void doSave(final IProgressMonitor monitor){ /* leer */
	}
	
	public void doSaveAs(){ /* leer */
	}
	
	public boolean isDirty(){
		return true;
	}
	
	public boolean isSaveAsAllowed(){
		return false;
	}
	
	public boolean isSaveOnCloseNeeded(){
		return true;
	}
	
	public void catchElexisEvent(ElexisEvent ev){
		relabel();
	}
	
	private static ElexisEvent template = new ElexisEvent(null, Patient.class,
		ElexisEvent.EVENT_SELECTED | ElexisEvent.EVENT_DESELECTED);
	
	public ElexisEvent getElexisEventFilter(){
		return template;
	}
}
