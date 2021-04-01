/*******************************************************************************
 * Copyright (c) 2006-2010, G. Weirich and Elexis
 * Copyright (c) 2016-2021, J. Sigle
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    G. Weirich - initial implementation
 *    J. Sigle   - Additional options in dropdown menu: "nicht nach Patient filtern (übergeht 'Anzeige nur beim Patienten')" und "nur vom ausgewählten Patienten (übergeht 'Immer in Pendenzen anzeigen')" 
 *    
 *******************************************************************************/
package ch.elexis.core.ui.views;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.viewers.IColorProvider;
import org.eclipse.jface.viewers.IFontProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

import ch.elexis.admin.AccessControlDefaults;
import ch.elexis.core.constants.Preferences;
import ch.elexis.core.constants.StringConstants;
import ch.elexis.core.data.activator.CoreHub;
import ch.elexis.core.data.events.ElexisEvent;
import ch.elexis.core.data.events.ElexisEventDispatcher;
import ch.elexis.core.data.events.ElexisEventListener;
import ch.elexis.core.data.events.Heartbeat.HeartListener;
import ch.elexis.core.lock.types.LockResponse;
import ch.elexis.core.model.issue.Priority;
import ch.elexis.core.model.issue.ProcessStatus;
import ch.elexis.core.model.issue.Type;
import ch.elexis.core.model.issue.Visibility;
import ch.elexis.core.ui.UiDesk;
import ch.elexis.core.ui.actions.GlobalEventDispatcher;
import ch.elexis.core.ui.actions.IActivationListener;
import ch.elexis.core.ui.actions.RestrictedAction;
import ch.elexis.core.ui.dialogs.ReminderDetailDialog;
import ch.elexis.core.ui.events.ElexisUiEventListenerImpl;
import ch.elexis.core.ui.icons.Images;
import ch.elexis.core.ui.locks.AcquireLockBlockingUi;
import ch.elexis.core.ui.locks.ILockHandler;
import ch.elexis.core.ui.locks.LockResponseHelper;
import ch.elexis.core.ui.util.SWTHelper;
import ch.elexis.core.ui.util.ViewMenus;
import ch.elexis.core.ui.util.viewers.CommonContentProviderAdapter;
import ch.elexis.core.ui.util.viewers.CommonViewer;
import ch.elexis.core.ui.util.viewers.DefaultLabelProvider;
import ch.elexis.core.ui.util.viewers.SimpleWidgetProvider;
import ch.elexis.core.ui.util.viewers.ViewerConfigurer;
import ch.elexis.core.ui.views.reminder.ReminderStatusSubMenu;

import ch.elexis.data.Anwender;
import ch.elexis.data.Patient;
import ch.elexis.data.PersistentObject;
import ch.elexis.data.Query;
import ch.elexis.data.Reminder;
import ch.rgw.io.Settings;
import ch.rgw.tools.TimeTool;

public class ReminderView extends ViewPart implements IActivationListener, HeartListener {
	
	public static final String ID = "ch.elexis.reminderview"; //$NON-NLS-1$
	
	private IAction newReminderAction, deleteReminderAction, showOnlyOwnDueReminderToggleAction,
			overrideShowFromAnyPatientReminderAction,				//201609150117js
			overrideShowOnlyFromCurrentPatientReminderAction,		//201609150117js
			showSelfCreatedReminderAction, toggleAutoSelectPatientAction, reloadAction;
	private IAction sortByDueDate;
	private RestrictedAction showOthersRemindersAction;
	private RestrictedAction selectPatientAction;
	
	private ReminderLabelProvider reminderLabelProvider = new ReminderLabelProvider();
	
	private IAction filterActionType[] = new IAction[Type.values().length];
	private Set<Integer> filterActionSet = new HashSet<Integer>();
	
	private long cvHighestLastUpdate = 0l;
	
	private int filterDueDateDays =
		CoreHub.userCfg.get(Preferences.USR_REMINDER_FILTER_DUE_DAYS, -1);
	private boolean autoSelectPatient =
		CoreHub.userCfg.get(Preferences.USR_REMINDER_AUTO_SELECT_PATIENT, false);
	private boolean showOnlyDueReminders =
		CoreHub.userCfg.get(Preferences.USR_REMINDERSOPEN, false);
	private boolean showOthersReminders = (CoreHub.userCfg.get(Preferences.USR_REMINDEROTHERS, false)
		&& CoreHub.acl.request(AccessControlDefaults.ADMIN_VIEW_OTHERS_REMINDERS));
	private boolean showSelfCreatedReminders =
		CoreHub.userCfg.get(Preferences.USR_REMINDEROWN, false);
	
	private CommonViewer cv = new CommonViewer();
	private ViewerConfigurer vc;
	private Query<Reminder> qbe;
	private ReminderFilter filter = new ReminderFilter();
	private Patient actPatient;
	private Text txtSearch;
	
	private ElexisEventListener eeli_reminder = new ElexisUiEventListenerImpl(Reminder.class,
		ElexisEvent.EVENT_RELOAD | ElexisEvent.EVENT_CREATE | ElexisEvent.EVENT_UPDATE) {
		public void catchElexisEvent(ElexisEvent ev){
			cv.notify(CommonViewer.Message.update);
		}
	};
	
	// 1079 - nur wenn der View offen ist werden bei Patienten-Wechsel die Reminders abgefragt!
	private ElexisEventListener eeli_pat = new ElexisUiEventListenerImpl(Patient.class) {
		
		public void runInUi(final ElexisEvent ev){
			if (((Patient) ev.getObject()).equals(actPatient)) {
				return;
			}
			actPatient = (Patient) ev.getObject();
			// clear selection before update
			cv.getViewerWidget().setSelection(StructuredSelection.EMPTY);
			
			Control control = cv.getViewerWidget().getControl();
			if (control != null && !control.isDisposed() && control.isVisible()) {
				cv.notify(CommonViewer.Message.update);
			}
			/**
			 * ch.elexis.core.data.events.PatientEventListener will be called on opposite
			 * Preferences.USR_SHOWPATCHGREMINDER condition.
			 */
			if (!CoreHub.userCfg.get(Preferences.USR_SHOWPATCHGREMINDER, true)) {
				UiDesk.asyncExec(new Runnable() {
					
					public void run(){
						List<Reminder> list = Reminder.findOpenRemindersResponsibleFor(
							CoreHub.actUser, false, (Patient) ev.getObject(), true);
						if (list.size() != 0) {
							StringBuilder sb = new StringBuilder();
							for (Reminder r : list) {
								sb.append(r.getSubject() + "\n");
								sb.append(r.getMessage() + "\n\n");
							}
							SWTHelper.alert(Messages.ReminderView_importantRemindersCaption,
								sb.toString());
						}
					}
				});
			}
		}
	};
	
	private ElexisEventListener eeli_user =
		new ElexisUiEventListenerImpl(Anwender.class, ElexisEvent.EVENT_USER_CHANGED) {
			
			public void runInUi(ElexisEvent ev){
				refreshUserConfiguration();
				
				Control control = cv.getViewerWidget().getControl();
				if (control != null && !control.isDisposed() && control.isVisible()) {
					cv.notify(CommonViewer.Message.update);
				}
				
			}
		};
	
	public ReminderView(){
		qbe = new Query<>(Reminder.class, null, null, Reminder.TABLENAME, new String[] {
			Reminder.FLD_DUE, Reminder.FLD_PRIORITY, Reminder.FLD_ACTION_TYPE, Reminder.FLD_CREATOR,
			Reminder.FLD_KONTAKT_ID
		});
	}
	
	@Override
	public void createPartControl(final Composite parent){
		Composite content = new Composite(parent, SWT.NONE);
		content.setLayout(new GridLayout(1, false));
		Composite header = new Composite(content, SWT.NONE);
		GridLayout gl_header = new GridLayout(2, false);
		gl_header.horizontalSpacing = 0;
		gl_header.marginWidth = 0;
		gl_header.marginHeight = 0;
		header.setLayout(gl_header);
		header.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		
		txtSearch = new Text(header, SWT.SEARCH);
		txtSearch.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		txtSearch.setMessage(Messages.ReminderView_txtSearch_message);
		txtSearch.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e){
				filter.setFilterText(txtSearch.getText());
				cv.notify(CommonViewer.Message.update_keeplabels);
			}
		});
		
		Label btnClear = new Label(header, SWT.NONE);
		btnClear.setImage(Images.IMG_CLEAR.getImage());
		btnClear.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDown(MouseEvent e){
				txtSearch.setText(StringConstants.EMPTY);
				filter.setFilterText(null);
				cv.notify(CommonViewer.Message.update_keeplabels);
			}
		});
		
		reminderLabelProvider.updateUserConfiguration();
		
		ReminderViewCommonContentProvider contentProvider = new ReminderViewCommonContentProvider();
		vc = new ViewerConfigurer(contentProvider, reminderLabelProvider, null,
			new ViewerConfigurer.DefaultButtonProvider(),
			new SimpleWidgetProvider(SimpleWidgetProvider.TYPE_LAZYLIST, SWT.MULTI, cv));
		
		makeActions(contentProvider);
		
		ViewMenus menu = new ViewMenus(getViewSite());
		menu.createToolbar(reloadAction, newReminderAction, toggleAutoSelectPatientAction);
		menu.createMenu(createActionList());
		
		if (CoreHub.acl.request(AccessControlDefaults.ADMIN_VIEW_OTHERS_REMINDERS)) {
			showOthersRemindersAction.setEnabled(true);
			showOthersRemindersAction
				.setChecked(CoreHub.userCfg.get(Preferences.USR_REMINDEROTHERS, false));
		} else {
			showOthersRemindersAction.setEnabled(false);
		}
		cv.create(vc, content, SWT.NONE, getViewSite());
		cv.addDoubleClickListener(new CommonViewer.DoubleClickListener() {
			public void doubleClicked(final PersistentObject obj, final CommonViewer cv){
				Reminder reminder = (Reminder) obj;
				AcquireLockBlockingUi.aquireAndRun(reminder, new ILockHandler() {
					@Override
					public void lockAcquired(){
						ReminderDetailDialog rdd =
							new ReminderDetailDialog(UiDesk.getTopShell(), (Reminder) obj);
						int retVal = rdd.open();
						if (retVal == Dialog.OK) {
							ElexisEventDispatcher.getInstance().fire(
								new ElexisEvent(reminder, getClass(), ElexisEvent.EVENT_UPDATE));
						}
					}
					
					@Override
					public void lockFailed(){
						cv.notify(CommonViewer.Message.update);
					}
				});
				
			}
		});
		
		List<IContributionItem> popupMenuActionList = new ArrayList<>();
		popupMenuActionList.add(new ReminderStatusSubMenu(cv));
		popupMenuActionList.add(new ActionContributionItem(deleteReminderAction));
		popupMenuActionList.add(new ActionContributionItem(selectPatientAction));
		popupMenuActionList.add(new Separator());
		popupMenuActionList.addAll(createActionList());
		menu.createViewerContextMenu(cv.getViewerWidget(), popupMenuActionList);
		cv.getViewerWidget().addFilter(filter);
		GlobalEventDispatcher.addActivationListener(this, getViewSite().getPart());
		
		cv.getViewerWidget().addSelectionChangedListener(new ISelectionChangedListener() {
			
			@Override
			public void selectionChanged(SelectionChangedEvent event){
				IStructuredSelection selection = (IStructuredSelection) event.getSelection();
				selectPatientAction.setEnabled(selection.size() <= 1);
				selectPatientAction.reflectRight();
				if (autoSelectPatient && selectPatientAction.isEnabled()) {
					selectPatientAction.doRun();
				}
			}
		});
		
		ElexisEventDispatcher.getInstance().addListeners(eeli_pat, eeli_user, eeli_reminder);
	}
	
	private List<IContributionItem> createActionList(){
		Action labelFilter = new Action("Anzeige filtern") {};
		labelFilter.setEnabled(false);
		
		MenuManager timeFilterSubMenu = new MenuManager("Zeitraum Anzeige");
		FilterTimeAction action30 = new FilterTimeAction(30);
		FilterTimeAction action60 = new FilterTimeAction(60);
		FilterTimeAction action90 = new FilterTimeAction(90);
		action30.setOthers(Arrays.asList(action60, action90));
		action60.setOthers(Arrays.asList(action30, action90));
		action90.setOthers(Arrays.asList(action30, action60));
		timeFilterSubMenu.add(action30);
		timeFilterSubMenu.add(action60);
		timeFilterSubMenu.add(action90);
		
		Action labelResponsibility = new Action("Anzeige erweitern") {};
		labelResponsibility.setEnabled(false);
		
		Action labelSorter = new Action("Anzeige sortieren") {};
		labelSorter.setEnabled(false);
		
		MenuManager typeFilterSubMenu = new MenuManager("Nach Aktionstyp");
		List<IContributionItem> ca =
			ViewMenus.convertActionsToContributionItems((filterActionType));
		for (IContributionItem iContributionItem : ca) {
			typeFilterSubMenu.add(iContributionItem);
		}
		return Arrays.asList(new ActionContributionItem(newReminderAction), null,
			new ActionContributionItem(labelSorter), new ActionContributionItem(sortByDueDate),
			null, new ActionContributionItem(labelFilter), timeFilterSubMenu,
			new ActionContributionItem(showOnlyOwnDueReminderToggleAction), typeFilterSubMenu, null,
			new ActionContributionItem(labelResponsibility),
			new ActionContributionItem(showSelfCreatedReminderAction),
			new ActionContributionItem(showOthersRemindersAction),
			//20210401js: commented this out, ShowOnlyFromCurrentPatient is a good addition esp. to "Alle"
			//null,																				
			new ActionContributionItem(overrideShowFromAnyPatientReminderAction), 				//201609150117js
			new ActionContributionItem(overrideShowOnlyFromCurrentPatientReminderAction),null	//201609150117js
			);
	}
	
	private void refreshUserConfiguration(){
		boolean bChecked = CoreHub.userCfg.get(Preferences.USR_REMINDERSOPEN, true);
		showOnlyOwnDueReminderToggleAction.setChecked(bChecked);
		showSelfCreatedReminderAction
			.setChecked(CoreHub.userCfg.get(Preferences.USR_REMINDEROWN, false));
		toggleAutoSelectPatientAction
			.setChecked(CoreHub.userCfg.get(Preferences.USR_REMINDER_AUTO_SELECT_PATIENT, false));
		
		// get state from user's configuration
		showOthersRemindersAction
			.setChecked(CoreHub.userCfg.get(Preferences.USR_REMINDEROTHERS, false));
		
		// update action's access rights
		showOthersRemindersAction.reflectRight();
		
		//201609150117js: Default is false for this new override option.
		overrideShowFromAnyPatientReminderAction.setChecked(											//201609150117js
			CoreHub.userCfg.get(Preferences.USR_REMINDERSOVERRIDESHOWFROMANYPATIENT, false));			//201609150117js
		
		//201609150117js: Default is false for this new override option.
		overrideShowOnlyFromCurrentPatientReminderAction.setChecked(									//201609150117js
			CoreHub.userCfg.get(Preferences.USR_REMINDERSOVERRIDESHOWONLYFROMCURRENTPATIENT, false));	//201609150117js
		
		reminderLabelProvider.updateUserConfiguration();
	}
	
	@Override
	public void setFocus(){}
	
	@Override
	public void dispose(){
		ElexisEventDispatcher.getInstance().removeListeners(eeli_pat, eeli_user, eeli_reminder);
		GlobalEventDispatcher.removeActivationListener(this, getViewSite().getPart());
		
		System.out.println("ReminderView: Preferences.USR_REMINDERSOPEN"+Preferences.USR_REMINDERSOPEN.toString());
		if (showOnlyOwnDueReminderToggleAction == null) System.out.println("ReminderView: showOnlyOwnDueReminderToggleAction IS NULL");
		else System.out.println("ReminderView: showOnlyOwnDueReminderToggleAction.isChecked()"+showOnlyOwnDueReminderToggleAction.isChecked());
		
		CoreHub.userCfg.set(Preferences.USR_REMINDERSOPEN,
			showOnlyOwnDueReminderToggleAction.isChecked());

		//ToDo: Review these lines - shall we keep them or leave them commented out? Same question for the line above!			//201609150117js
		//This would probably dispose of the respective settings at the end of a session,										//201609150117js
		//but I would rather like to keep them - because JH will probably want to use them regularly.							//201609150117js
		//CoreHub.userCfg.set(PreferenceConstants.USR_REMINDERSOVERRIDESHOWFROMANYPATIENT, overrideShowFromAnyPatientReminderAction.isChecked());			//201609150117js
		//CoreHub.userCfg.set(PreferenceConstants.USR_REMINDERSOVERRIDESHOWONLYFROMCURRENTPATIENT, overrideShowOnlyFromCurrentPatientReminderAction.isChecked());	//201609150117js
	}
	
	private Images determineActionTypeImage(Type actionType){
		switch (actionType) {
		case PRINT:
		case PRINT_DRUG_STICKER:
			return Images.IMG_PRINTER;
		case MAKE_APPOINTMENT:
			return Images.IMG_CALENDAR;
		case DISPENSE_MEDICATION:
			return Images.IMG_PILL;
		case PROCESS_SERVICE_RECORDING:
			return Images.IMG_MONEY;
		case CHECK_LAB_RESULT:
		case READ_DOCUMENT:
			return Images.IMG_EYE_WO_SHADOW;
		case SEND_DOCUMENT:
			return Images.IMG_MAIL_SEND;
		default:
			return Images.IMG_EMPTY_TRANSPARENT;
		}
	}
	
	private void makeActions(final ReminderViewCommonContentProvider contentProvider){
		newReminderAction = new Action(Messages.ReminderView_newReminderAction) { //$NON-NLS-1$
			{
				setImageDescriptor(Images.IMG_NEW.getImageDescriptor());
				setToolTipText(Messages.ReminderView_newReminderToolTip); //$NON-NLS-1$
			}
			
			@Override
			public void run(){
				ReminderDetailDialog erd = null;
				Object[] sel = cv.getSelection();
				if (sel != null && sel.length == 1 && sel[0] instanceof Reminder) {
					Reminder reminder = (Reminder) sel[0];
					erd = new ReminderDetailDialog(getViewSite().getShell(), reminder.getDateDue());
				} else {
					 erd = new ReminderDetailDialog(getViewSite().getShell());
				}
				int retVal = erd.open();
				if (retVal == Dialog.OK) {
					Reminder reminder = erd.getReminder();
					CoreHub.getLocalLockService().acquireLock(reminder);
					CoreHub.getLocalLockService().releaseLock(reminder);
				}
				cv.notify(CommonViewer.Message.update_keeplabels);
			}
		};
		deleteReminderAction = new Action(Messages.ReminderView_deleteAction) {
			{
				setImageDescriptor(Images.IMG_DELETE.getImageDescriptor());
				setToolTipText(Messages.ReminderView_deleteToolTip);
			}
			
			@Override
			public void run(){
				Object[] selections = cv.getSelection();
				if (selections != null && selections.length == 1
					&& selections[0] instanceof Reminder) {
					Reminder r = (Reminder) selections[0];
					LockResponse lockResponse = CoreHub.getLocalLockService().acquireLock(r);
					if (lockResponse.isOk()) {
						r.delete();
						CoreHub.getLocalLockService().releaseLock(r);
					} else {
						LockResponseHelper.showInfo(lockResponse, r, null);
					}
					
					cv.notify(CommonViewer.Message.update_keeplabels);
				}
			}
			
			@Override
			public boolean isEnabled(){
				Object[] sel = cv.getSelection();
				return (sel != null && sel.length == 1 && sel[0] instanceof Reminder);
			}
		};
		sortByDueDate = new Action(Messages.ReminderView_sortByDueDate, Action.AS_CHECK_BOX) {
			
			int state = 0;
			
			@Override
			public void run(){
				
				if (state == 0) {
					contentProvider.setComparator(new Comparator<Reminder>() {
						@Override
						public int compare(Reminder o1, Reminder o2){
							return TimeTool.compare(o2.getDateDue(), o1.getDateDue());
						}
					});
					state = 1;
					sortByDueDate.setText(Messages.ReminderView_sortByDueDateAscending);
					sortByDueDate.setChecked(true);
				} else if (state == 1) {
					contentProvider.setComparator(new Comparator<Reminder>() {
						@Override
						public int compare(Reminder o1, Reminder o2){
							return TimeTool.compare(o1.getDateDue(), o2.getDateDue());
						}
					});
					state = 2;
					sortByDueDate.setText(Messages.ReminderView_sortByDueDateDescending);
					sortByDueDate.setChecked(true);
				} else if (state == 2) {
					contentProvider.setComparator(null);
					state = 0;
					sortByDueDate.setText(Messages.ReminderView_sortByDueDate);
					sortByDueDate.setChecked(false);
				}
				
				cv.notify(CommonViewer.Message.update_keeplabels);
			}
		};
		
		showOnlyOwnDueReminderToggleAction =
			new Action(Messages.ReminderView_onlyDueAction, Action.AS_CHECK_BOX) { //$NON-NLS-1$
				{
					setToolTipText(Messages.ReminderView_onlyDueToolTip); //$NON-NLS-1$
				}
				
				@Override
				public void run(){
					showOnlyDueReminders = showOnlyOwnDueReminderToggleAction.isChecked();
					CoreHub.userCfg.set(Preferences.USR_REMINDERSOPEN, showOnlyDueReminders);
					cv.notify(CommonViewer.Message.update_keeplabels);
				}
			};
			
		showSelfCreatedReminderAction =
			new Action(Messages.ReminderView_myRemindersAction, Action.AS_CHECK_BOX) { //$NON-NLS-1$
				{
					setToolTipText(Messages.ReminderView_myRemindersToolTip); //$NON-NLS-1$
				}
				
				@Override
				public void run(){
					showSelfCreatedReminders = showSelfCreatedReminderAction.isChecked();
					CoreHub.userCfg.set(Preferences.USR_REMINDEROWN, showSelfCreatedReminders);
					cv.notify(CommonViewer.Message.update_keeplabels);
				}
			};
		showOthersRemindersAction =
			new RestrictedAction(AccessControlDefaults.ADMIN_VIEW_OTHERS_REMINDERS,
				Messages.ReminderView_foreignAction, Action.AS_CHECK_BOX) {
				{
					setToolTipText(Messages.ReminderView_foreignTooltip);
					setImageDescriptor(Images.IMG_ACHTUNG.getImageDescriptor());
				}
				
				@Override
				public void doRun(){
					showOthersReminders = showOthersRemindersAction.isChecked();
					CoreHub.userCfg.set(Preferences.USR_REMINDEROTHERS, showOthersReminders);
					cv.notify(CommonViewer.Message.update_keeplabels);
				}
			};
					
		//201609150117js: originally added this block. 20210331js: restored adopted version to 3.7js
		overrideShowFromAnyPatientReminderAction =
			new Action(Messages.ReminderView_overrideShowFromAnyPatientReminderAction, Action.AS_CHECK_BOX) { //$NON-NLS-1$
				{
					setToolTipText(Messages.ReminderView_overrideShowFromAnyPatientReminderToolTip); //$NON-NLS-1$
					setImageDescriptor(Images.IMG_ACHTUNG.getImageDescriptor());
				}
				
				@Override
				public void run(){
					boolean bChecked = overrideShowFromAnyPatientReminderAction.isChecked();
					CoreHub.userCfg.set(Preferences.USR_REMINDERSOVERRIDESHOWFROMANYPATIENT, bChecked);
					
					//overrideShowFromAnyPatientReminderAction and overrideShowOnlyFromCurrentPatientReminderAction
					//CANNOT be active at the same time, they are mutually exclusive in user understanding,
					//and in the filtering program code below, if overrideShowOnlyFromCurrentPatientReminderAction is true,
					//then overrideShowOnlyFromCurrentPatientReminderAction will actually be ignored.
					if (bChecked) {
						overrideShowOnlyFromCurrentPatientReminderAction.setChecked(false);
						CoreHub.userCfg.set(Preferences.USR_REMINDERSOVERRIDESHOWONLYFROMCURRENTPATIENT, false);
					}
						
					cv.notify(CommonViewer.Message.update_keeplabels);
				}
			};

		//201609150117js: originally added this block. 20210331js: restored adopted version to 3.7js
		overrideShowOnlyFromCurrentPatientReminderAction =
			new Action(Messages.ReminderView_overrideShowOnlyFromCurrentPatientReminderAction, Action.AS_CHECK_BOX) { //$NON-NLS-1$
				{
					setToolTipText(Messages.ReminderView_overrideShowOnlyFromCurrentPatientReminderTooltip); //$NON-NLS-1$
					setImageDescriptor(Images.IMG_ACHTUNG.getImageDescriptor());
				}
			
				@Override
				public void run(){
					boolean bChecked = overrideShowOnlyFromCurrentPatientReminderAction.isChecked();
					CoreHub.userCfg.set(Preferences.USR_REMINDERSOVERRIDESHOWONLYFROMCURRENTPATIENT, bChecked);
						
					//overrideShowFromAnyPatientReminderAction and overrideShowOnlyFromCurrentPatientReminderAction
					//CANNOT be active at the same time, they are mutually exclusive in user understanding,
					//and in the filtering program code below, if overrideShowOnlyFromCurrentPatientReminderAction is true,
					//then overrideShowOnlyFromCurrentPatientReminderAction will actually be ignored.
					if (bChecked) {
						overrideShowFromAnyPatientReminderAction.setChecked(false);
						CoreHub.userCfg.set(Preferences.USR_REMINDERSOVERRIDESHOWFROMANYPATIENT, false);
					}

					cv.notify(CommonViewer.Message.update_keeplabels);
				}
			};
		
		selectPatientAction = new RestrictedAction(AccessControlDefaults.PATIENT_DISPLAY,
			Messages.ReminderView_activatePatientAction, Action.AS_UNSPECIFIED) {
			{
				setImageDescriptor(Images.IMG_PERSON.getImageDescriptor());
				setToolTipText(Messages.ReminderView_activatePatientTooltip);
			}
			
			public void doRun(){
				Object[] sel = cv.getSelection();
				if (sel != null && sel.length > 1) {
					SWTHelper.showInfo(Messages.ReminderView_onePatOnly,
						Messages.ReminderView_onlyOnePatientForActivation);
				} else if (sel != null && sel.length > 0) {
					Reminder reminder = (Reminder) sel[0];
					Patient patient = reminder.getKontakt();
					Anwender creator = reminder.getCreator();
					if (patient != null) {
						if (!patient.getId().equals(creator.getId())) {
							ElexisEventDispatcher.fireSelectionEvent(patient);
						}
					}
				}
			}
			
			@Override
			public boolean isEnabled(){
				Object[] sel = cv.getSelection();
				if (sel != null && sel.length == 1 && sel[0] instanceof Reminder) {
					Reminder reminder = (Reminder) sel[0];
					return reminder.isPatientRelated();
				}
				return false;
			}
		};
		
		toggleAutoSelectPatientAction =
			new Action(Messages.ReminderView_activatePatientAction, Action.AS_CHECK_BOX) {
				{
					setImageDescriptor(Images.IMG_PERSON.getImageDescriptor());
					setToolTipText(Messages.ReminderView_toggleSelectPatientActionTooltip);
					setChecked(autoSelectPatient);
				}
				
				@Override
				public void run(){
					autoSelectPatient = toggleAutoSelectPatientAction.isChecked();
					CoreHub.userCfg.set(Preferences.USR_REMINDER_AUTO_SELECT_PATIENT,
						autoSelectPatient);
				}
			};
		
		reloadAction = new Action(Messages.PatHeuteView_reloadAction) { //$NON-NLS-1$
			{
				setImageDescriptor(Images.IMG_REFRESH.getImageDescriptor());
				setToolTipText(Messages.PatHeuteView_reloadToolTip); //$NON-NLS-1$
			}
			
			@Override
			public void run(){
				heartbeat();
			}
		};
		
		for (int i = 0; i < Type.values().length; i++) {
			Type type = Type.values()[i];
			filterActionType[i] = new Action(type.getLocaleText(), Action.AS_CHECK_BOX) {
				@Override
				public void run(){
					if (isChecked()) {
						filterActionSet.add(type.numericValue());
					} else {
						filterActionSet.remove(type.numericValue());
					}
					cv.notify(CommonViewer.Message.update_keeplabels);
				}
			};
			filterActionType[i].setChecked(filterActionSet.contains(type.numericValue()));
			filterActionType[i]
				.setImageDescriptor(determineActionTypeImage(type).getImageDescriptor());
		}
	}
	
	public void activation(final boolean mode){
		/* egal */
	}
	
	public void visible(final boolean mode){
		if (mode) {
			cv.notify(CommonViewer.Message.update);
			CoreHub.heart.addListener(this);
			heartbeat();
			refreshUserConfiguration();
		} else {
			CoreHub.heart.removeListener(this);
		}
	}
	
	public void heartbeat(){
		long highestLastUpdate = PersistentObject.getHighestLastUpdate(Reminder.TABLENAME);
		if (highestLastUpdate > cvHighestLastUpdate) {
			cv.notify(CommonViewer.Message.update);
			cvHighestLastUpdate = highestLastUpdate;
		}
	}
	
	private class ReminderLabelProvider extends DefaultLabelProvider
			implements IColorProvider, IFontProvider {
		
		private Font boldFont;
		private Color colorInProgress;
		private Color colorDue;
		private Color colorOverdue;
		private Color colorOpen;
		
		public Color getBackground(final Object element){
			if (element instanceof Reminder) {
				Reminder reminder = (Reminder) element;
				switch (reminder.getDueState()) {
				case 1:
					return colorDue;
				case 2:
					return colorOverdue;
				default:
					ProcessStatus processStatus = reminder.getProcessStatus();
					if (ProcessStatus.OPEN == processStatus) {
						return colorOpen;
					} else if (ProcessStatus.IN_PROGRESS == processStatus) {
						return colorInProgress;
					}
					return null;
				}
			}
			return UiDesk.getColor(UiDesk.COL_BLACK);
		}
		
		public void updateUserConfiguration(){
			Settings cfg = CoreHub.userCfg.getBranch(Preferences.USR_REMINDERCOLORS, true);
			colorInProgress = UiDesk
				.getColorFromRGB(cfg.get(ProcessStatus.IN_PROGRESS.name(), "FFFFFF")); //$NON-NLS-1$;
			colorDue = UiDesk.getColorFromRGB(cfg.get(ProcessStatus.DUE.name(), "FFFFFF")); //$NON-NLS-1$;
			colorOverdue =
				UiDesk.getColorFromRGB(cfg.get(ProcessStatus.OVERDUE.name(), "FF0000")); //$NON-NLS-1$
			colorOpen =
				UiDesk.getColorFromRGB(cfg.get(ProcessStatus.OPEN.name(), "00FF00")); //$NON-NLS-1$
		}
		
		public Color getForeground(final Object element){
			if (element instanceof Reminder) {
				Reminder reminder = (Reminder) element;
				Priority prio = reminder.getPriority();
				if (Priority.LOW == prio) {
					return UiDesk.getColor(UiDesk.COL_GREY);
				}
				return null;
			}
			
			return UiDesk.getColor(UiDesk.COL_WHITE);
		}
		
		@Override
		public Image getColumnImage(Object element, int columnIndex){
			if (element instanceof Reminder) {
				Reminder reminder = (Reminder) element;
				
				ProcessStatus status = reminder.getStatus();
				if (ProcessStatus.CLOSED == status) {
					return Images.IMG_TICK.getImage();
				}
				
				Type actionType = reminder.getActionType();
				return determineActionTypeImage(actionType).getImage();
			}
			return null;
		}
		
		@Override
		public String getColumnText(Object element, int column){
			if (element instanceof Reminder) {
				Reminder reminder = (Reminder) element;
				ProcessStatus status = reminder.getStatus();
				if (ProcessStatus.ON_HOLD == status) {
					return "[" + status.getLocaleText() + "] " + super.getText(element);
				}
			}
			return super.getText(element);
		}
		
		@Override
		public Font getFont(Object element){
			if (element instanceof Reminder) {
				Reminder reminder = (Reminder) element;
				if (boldFont == null) {
					Display disp = Display.getCurrent();
					Font defaultFont = cv.getViewerWidget().getControl().getFont();
					FontDescriptor boldDescriptor =
						FontDescriptor.createFrom(defaultFont).setStyle(SWT.BOLD);
					boldFont = boldDescriptor.createFont(disp);
				}
				Priority prio = reminder.getPriority();
				if (Priority.HIGH == prio) {
					return boldFont;
				}
			}
			return null;
		}
	}
	
	private class ReminderFilter extends ViewerFilter {
		
		private String filterText;
		
		@Override
		public boolean select(final Viewer viewer, final Object parentElement, 
			final Object element) {
			
			if (element instanceof Reminder) {
				Reminder check = (Reminder) element;
				
				//201609150117js: The following preexisting code removes certain reminders from the list,
				//based on their status, onlyOpenReminderAction.isChecked, and .getDateDue().
				//N.B.: An "open" reminder is one, who has not been marked "completed" or "done" yet.
				
				if (showOnlyOwnDueReminderToggleAction.isChecked()) {
					int determineDueState = Reminder.determineDueState(check.getDateDue());
					if (determineDueState == 0) {
						return false;
					}
				}

				//201609150117js: The following (partly preexisting) code removes certain reminders from the list,
				//based upon the flags: "Anzeige nur beim Patienten" = no special Reminder.Typ.*, as far as I can see, 
				//and "Immer in Pendenzen anzeigen" = Reminder.Typ.anzeigeToDoAll.
				
				//I added two override options in the pulldown menu here:
				//overrideShowFromAnyPatientReminderAction.isChecked() and
				//overrideShowOnlyFromCurrentPatientReminderAction.isChecked().
				
				//These are both *different* from what stock Elexis 3.x originally provides:
				
				//Even now, the option labeled "Alle" in stock Elexis 3.x does not mean
				//"show reminders related to all patients" - but: "show reminders for any user being responsible".
				//And in the third section, stock Elexis 3.x shows only those reminders
				//So my overrideShowFromAnyPatientReminderAction is still useful.
				
				//The other way round, my overrideShowOnlyFromCurrentPatientReminderAction
				//can stop the display even of those reminders with the "show always" flag,
				//if data protection or a very compact list are truly desired.
								
				//201609150117js: Checking reminder IdentID vs. selected patient ID can be overridden,
				//so that reminders from any patient are shown, no matter which one is selected.
				//This overrides the reminder option: "Anzeige nur beim Patienten"
					 
				Patient act = ElexisEventDispatcher.getSelectedPatient();
				String patientId = (act != null) ? act.getId() : "INVALID_ID";
					
				String[] vals = check.get(true,
						Reminder.FLD_SUBJECT, Reminder.FLD_MESSAGE,
						Reminder.FLD_KONTAKT_ID, Reminder.FLD_VISIBILITY);

				if (  !overrideShowFromAnyPatientReminderAction.isChecked()
				   && !vals[2].equals(patientId)) {
						
					Visibility vis = Visibility.byNumericSafe(vals[3]);

					//20210401js: This override can even gain priority over
					//"Visibility Always" - but NOT over "Popup_on_login"
					if (  overrideShowOnlyFromCurrentPatientReminderAction.isChecked() //201609150117js
					   && (vis != Visibility.POPUP_ON_LOGIN) ) 
						return false;
						
					if (  vis != Visibility.ALWAYS 
					   && vis != Visibility.POPUP_ON_LOGIN )
						// other (non-selected patient) and not marked always visible
						return false;	
				}
				
				if (filterText != null && filterText.length() > 0) {
					if (!StringUtils.containsIgnoreCase(vals[0], filterText)
						&& !StringUtils.containsIgnoreCase(vals[1], filterText)) {
					return false;
					}
				}
				
			}
			return true;
		}
		
		public void setFilterText(String text){
			filterText = text;
		}
	}
	
	private class FilterTimeAction extends Action {
		
		private List<FilterTimeAction> others;
		private int days;
		
		public FilterTimeAction(int days){
			super(String.format("nächste %d Tage", days), Action.AS_CHECK_BOX);
			this.days = days;
			if (filterDueDateDays == days) {
				setChecked(true);
			}
		}
		
		public void setOthers(List<FilterTimeAction> list){
			this.others = list;
		}
		
		@Override
		public void run(){
			if (isChecked()) {
				CoreHub.userCfg.set(Preferences.USR_REMINDER_FILTER_DUE_DAYS, days);
				filterDueDateDays = days;
				cv.notify(CommonViewer.Message.update_keeplabels);
			} else {
				CoreHub.userCfg.set(Preferences.USR_REMINDER_FILTER_DUE_DAYS, -1);
				filterDueDateDays = -1;
				cv.notify(CommonViewer.Message.update_keeplabels);
			}
			
			if (others != null) {
				for (FilterTimeAction other : others) {
					other.setChecked(false);
				}
			}
		}
	}
	
	private class ReminderViewCommonContentProvider extends CommonContentProviderAdapter {
		
		private Comparator<Reminder> comparator;
		
		@Override
		public Object[] getElements(final Object inputElement){
			// Display reminders only if one is logged in
			if (CoreHub.actUser == null) {
				return new Object[0];
			}
			
			SortedSet<Reminder> reminders = new TreeSet<Reminder>();
			
			if (showOthersReminders
				&& CoreHub.acl.request(AccessControlDefaults.ADMIN_VIEW_OTHERS_REMINDERS)) {
				qbe.clear();
				if(filterDueDateDays != -1) {
					applyDueDateFilter(qbe);
				}
				reminders.addAll(qbe.execute());
			} else {
				reminders.addAll(Reminder.findOpenRemindersResponsibleFor(CoreHub.actUser,
					showOnlyDueReminders, filterDueDateDays, null, false));
				
				if (showSelfCreatedReminders) {
					qbe.clear();
					qbe.add(Reminder.FLD_CREATOR, Query.EQUALS, CoreHub.actUser.getId());
					if (filterDueDateDays != -1) {
						applyDueDateFilter(qbe);
					}
					reminders.addAll(qbe.execute());
				}
			}
			
			if (filterActionSet.size() > 0) {
				reminders
					.removeIf(p -> !(filterActionSet.contains(p.getActionType().numericValue())));
			}
			
			// split into sublists
			List<Reminder> patientRelatedReminders = new ArrayList<>();
			List<Reminder> patientRelatedRemindersCurrentPatient = new ArrayList<>();
			List<Reminder> otherReminders = new ArrayList<>();
			
			Patient currentPatient = ElexisEventDispatcher.getSelectedPatient();
			for (Reminder reminder : reminders) {
				if (reminder.isPatientRelated()) {
					if (currentPatient != null
						&& reminder.get(Reminder.FLD_KONTAKT_ID).equals(currentPatient.getId())) {
						patientRelatedRemindersCurrentPatient.add(reminder);
					} else {
						patientRelatedReminders.add(reminder);
					}
				} else {
					otherReminders.add(reminder);
				}
			}
			
			if (comparator != null) {
				Collections.sort(patientRelatedReminders, comparator);
				Collections.sort(patientRelatedRemindersCurrentPatient, comparator);
				Collections.sort(otherReminders, comparator);
			}
			
			List<Object> resultList = new ArrayList<>();
			
			//20210401js: As first entry, show responsible user and some options from the pulldown menu.
			//Alternatively, we could add 3d toggle buttons at the top of the window -
			//that would be nicer, but need more work than I want to do now.
			StringBuilder selectedOptions = new StringBuilder(Messages.ReminderView_assignTo+": ");
			if (showOthersReminders)
				selectedOptions.append(Messages.ReminderView_assignToUnfiltered+"     ");
			else
				selectedOptions.append(CoreHub.actUser.getKuerzel()+"     ");
			
			if (showOnlyOwnDueReminderToggleAction.isChecked())
				selectedOptions.append(Messages.ReminderView_onlyDueAction+"     ");
			
			if (showSelfCreatedReminders)
				selectedOptions.append(Messages.ReminderView_myRemindersAction+"     ");			
				
			resultList.add(selectedOptions);
			
			//TODO: Move the following strings into the messages*.properties
			//and 2 x Messages.java files, and translate them.
			
			//TODO: Review the exact functionality of the "Von mir erstellte" menu option 
			
			//20210401js: 
			if (actPatient == null) resultList.add("------------ Aktueller Patient: -");
			else 	resultList.add("------------ Aktueller Patient:  "
					+actPatient.getName()+" "+actPatient.getVorname()+"  "
					+actPatient.getGeburtsdatum()+"  ("+actPatient.getAlter()+")  "+actPatient.getGeschlecht());
			resultList.addAll(patientRelatedRemindersCurrentPatient);
			
			//20210401js: The section "Allgemein" is kept visible
			//even when overrideShowOnlyFromCurrentPatientReminderAction is set:
			//We don't want to miss individual responsibilities even when we
			//use this flag to keep other patient's information off the display
			//for data protection reasons.
			resultList.add("------------ Allgemein");
			resultList.addAll(otherReminders);
			
			//20210401js: Don't show the (then misleading) extra section headings
			//when overrideShowOnlyFromCurrentPatientReminderAction is checked -
			//in that case, show a note so users know *that* and *why* the display ends here.
			//Please note: Reminders with popup_at_login should still be displayed
			//as (1) they are probably the most important ones,
			//and (b) they don't appear on this list and are visible only in a controlled situation.
			if (overrideShowOnlyFromCurrentPatientReminderAction.isChecked())
				resultList.add("------------ "+Messages.ReminderView_overrideShowOnlyFromCurrentPatientReminderAction);
			else {			
				//20210401js: For reminders related to other patients:
				//show them either because the reminder has the "Immer anzeigen" property,
				//or because overrideShowFromAnyPatientReminderAction is checked -
				//and show the currently active option in the line above that section of the list.
				if (overrideShowFromAnyPatientReminderAction.isChecked())
					resultList.add("------------ Patientenbezogen - für andere Patienten - "
							+Messages.ReminderView_overrideShowFromAnyPatientReminderAction);
				else
					resultList.add("------------ Patientenbezogen - für andere Patienten, aber mit \"immer anzeigen\"");
				resultList.addAll(patientRelatedReminders);
			}
			
			return resultList.toArray();
		}
		
		private void applyDueDateFilter(Query<Reminder> qbe){
			TimeTool dueDateDays = new TimeTool();
			dueDateDays.addDays(filterDueDateDays);
			qbe.add(Reminder.FLD_DUE, Query.NOT_EQUAL, "");
			qbe.add(Reminder.FLD_DUE, Query.LESS_OR_EQUAL,
				dueDateDays.toString(TimeTool.DATE_COMPACT));
		}
		
		public void setComparator(Comparator<Reminder> comparator){
			this.comparator = comparator;
		}
	}
}
