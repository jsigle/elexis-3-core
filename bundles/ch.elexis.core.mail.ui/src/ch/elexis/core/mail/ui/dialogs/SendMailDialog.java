package ch.elexis.core.mail.ui.dialogs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.fieldassist.ContentProposalAdapter;
import org.eclipse.jface.fieldassist.IContentProposal;
import org.eclipse.jface.fieldassist.IContentProposalListener;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.handlers.IHandlerService;
import org.slf4j.LoggerFactory;

import ch.elexis.core.mail.MailAccount;
import ch.elexis.core.mail.MailAccount.TYPE;
import ch.elexis.core.mail.MailMessage;
import ch.elexis.core.mail.MailTextTemplate;
import ch.elexis.core.mail.TaskUtil;
import ch.elexis.core.mail.ui.client.MailClientComponent;
import ch.elexis.core.model.IMandator;
import ch.elexis.core.model.ITextTemplate;
import ch.elexis.core.services.ITextReplacementService;
import ch.elexis.core.services.holder.ContextServiceHolder;
import ch.elexis.core.services.holder.StoreToStringServiceHolder;
import ch.elexis.core.tasks.model.ITaskDescriptor;
import ch.elexis.core.ui.dialogs.KontaktSelektor;
import ch.elexis.core.ui.util.CoreUiUtil;
import ch.elexis.data.Kontakt;

public class SendMailDialog extends TitleAreaDialog {
	
	@Inject
	private ITextReplacementService textReplacement;
	
	private ComboViewer accountsViewer;
	private MailAccount account;
	private Text toText;
	private String toString = "";
	private Text ccText;
	private String ccString = "";
	private Text subjectText;
	private String subjectString = "";
	private Text textText;
	private String textString = "";
	private AttachmentsComposite attachments;
	
	private Command createOutboxCommand;
	private String accountId;
	private String attachmentsString;
	private String documentsString;
	private boolean disableOutbox;
	private ComboViewer templatesViewer;
	
	public SendMailDialog(Shell parentShell){
		super(parentShell);
		setShellStyle(getShellStyle() | SWT.RESIZE);
		
		CoreUiUtil.injectServices(this);
		
		ICommandService commandService =
			(ICommandService) PlatformUI.getWorkbench().getService(ICommandService.class);
		createOutboxCommand =
			commandService.getCommand("at.medevit.elexis.outbox.ui.command.createElementNoUi");
	}
	
	@Override
	protected Control createDialogArea(Composite parent){
		if (MailClientComponent.getMailClient() != null) {
			setTitle("E-Mail versenden");
		} else {
			setTitle("E-Mail versand nicht möglich");
		}
		
		Composite area = (Composite) super.createDialogArea(parent);
		Composite container = new Composite(area, SWT.NONE);
		container.setLayout(new GridLayout(2, false));
		container.setLayoutData(new GridData(GridData.FILL_BOTH));
		
		if (MailClientComponent.getMailClient() != null) {
			Label lbl = new Label(container, SWT.NONE);
			lbl.setText("Von");
			accountsViewer = new ComboViewer(container);
			accountsViewer.setContentProvider(ArrayContentProvider.getInstance());
			accountsViewer.setLabelProvider(new LabelProvider());
			accountsViewer.setInput(getSendMailAccounts());
			accountsViewer.getControl()
				.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			if (accountId != null) {
				accountsViewer.setSelection(new StructuredSelection(accountId));
			}
			
			lbl = new Label(container, SWT.NONE);
			lbl.setText("An");
			toText = new Text(container, SWT.BORDER);
			toText.setText(toString);
			toText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			ContentProposalAdapter toAddressProposalAdapter = new ContentProposalAdapter(toText,
				new TextContentAdapter(), new MailAddressContentProposalProvider(), null, null);
			toAddressProposalAdapter.addContentProposalListener(new IContentProposalListener() {
				@Override
				public void proposalAccepted(IContentProposal proposal){
					int index =
						MailAddressContentProposalProvider.getLastAddressIndex(toText.getText());
					StringBuilder sb = new StringBuilder();
					if (index != 0) {
						sb.append(toText.getText().substring(0, index)).append(", ")
							.append(proposal.getContent());
					} else {
						sb.append(proposal.getContent());
					}
					toText.setText(sb.toString());
					toText.setSelection(toText.getText().length());
				}
			});
			MenuManager menuManager = new MenuManager();
			menuManager.add(new Action("email zu Kontakt") {
				@Override
				public void run(){
					KontaktSelektor selector =
						new KontaktSelektor(getShell(), Kontakt.class, "Kontakt auswahl",
							"Kontakt für die E-Mail Adresse auswählen", Kontakt.DEFAULT_SORT);
					if (selector.open() == Dialog.OK) {
						Kontakt selected = (Kontakt) selector.getSelection();
						selected.set(Kontakt.FLD_E_MAIL, toText.getSelectionText());
					}
				}
				
				@Override
				public boolean isEnabled(){
					String text = toText.getSelectionText();
					return text != null && !text.isEmpty() && text.contains("@");
				}
			});
			menuManager.addMenuListener(new IMenuListener() {
				@Override
				public void menuAboutToShow(IMenuManager manager){
					IContributionItem[] items = manager.getItems();
					for (IContributionItem iContributionItem : items) {
						iContributionItem.update();
					}
				}
			});
			toText.setMenu(menuManager.createContextMenu(toText));
			
			lbl = new Label(container, SWT.NONE);
			lbl.setText("Cc");
			ccText = new Text(container, SWT.BORDER);
			ccText.setText(ccString);
			ccText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			ContentProposalAdapter ccAddressProposalAdapter = new ContentProposalAdapter(ccText,
				new TextContentAdapter(), new MailAddressContentProposalProvider(), null, null);
			ccAddressProposalAdapter.addContentProposalListener(new IContentProposalListener() {
				@Override
				public void proposalAccepted(IContentProposal proposal){
					int index =
						MailAddressContentProposalProvider.getLastAddressIndex(ccText.getText());
					StringBuilder sb = new StringBuilder();
					if (index != 0) {
						sb.append(ccText.getText().substring(0, index)).append(", ")
							.append(proposal.getContent());
					} else {
						sb.append(proposal.getContent());
					}
					ccText.setText(sb.toString());
					ccText.setSelection(ccText.getText().length());
				}
			});
			menuManager = new MenuManager();
			menuManager.add(new Action("email zu Kontakt") {
				@Override
				public void run(){
					KontaktSelektor selector =
						new KontaktSelektor(getShell(), Kontakt.class, "Kontakt auswahl",
							"Kontakt für die E-Mail Adresse auswählen", Kontakt.DEFAULT_SORT);
					if (selector.open() == Dialog.OK) {
						Kontakt selected = (Kontakt) selector.getSelection();
						selected.set(Kontakt.FLD_E_MAIL, ccText.getSelectionText());
					}
				}
				
				@Override
				public boolean isEnabled(){
					String text = ccText.getSelectionText();
					return text != null && !text.isEmpty() && text.contains("@");
				}
			});
			menuManager.addMenuListener(new IMenuListener() {
				@Override
				public void menuAboutToShow(IMenuManager manager){
					IContributionItem[] items = manager.getItems();
					for (IContributionItem iContributionItem : items) {
						iContributionItem.update();
					}
				}
			});
			ccText.setMenu(menuManager.createContextMenu(ccText));

			lbl = new Label(container, SWT.NONE);
			lbl.setText("Betreff");
			subjectText = new Text(container, SWT.BORDER);
			subjectText.setText(subjectString);
			subjectText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			
			attachments = new AttachmentsComposite(container, SWT.NONE);
			attachments.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
			attachments.setAttachments(attachmentsString);
			attachments.setDocuments(documentsString);
			
			lbl = new Label(container, SWT.NONE);
			lbl.setText("Vorlage");
			templatesViewer = new ComboViewer(container);
			templatesViewer.getControl()
				.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			templatesViewer.setContentProvider(new ArrayContentProvider());
			templatesViewer.setLabelProvider(new LabelProvider() {
				@Override
				public String getText(Object element){
					if (element instanceof ITextTemplate) {
						return ((ITextTemplate) element).getName()
							+ (((ITextTemplate) element).getMandator() != null
									? " (" + ((ITextTemplate) element).getMandator().getLabel()
										+ ")"
									: "");
					}
					return super.getText(element);
				}
			});
			List<Object> templatesInput = new ArrayList<>();
			templatesInput.add("Keine Vorlage");
			templatesInput.addAll(MailTextTemplate.load());
			templatesViewer.setInput(templatesInput);
			templatesViewer.addSelectionChangedListener(new ISelectionChangedListener() {
				@Override
				public void selectionChanged(SelectionChangedEvent event){
					if (event.getStructuredSelection() != null && event.getStructuredSelection()
						.getFirstElement() instanceof ITextTemplate) {
						ITextTemplate selectedTemplate =
							(ITextTemplate) event.getStructuredSelection().getFirstElement();
						textText.setText(textReplacement.performReplacement(
							ContextServiceHolder.get().getRootContext(),
							selectedTemplate.getTemplate()));
					} else {
						textText.setText("");
					}
				}
			});
			
			lbl = new Label(container, SWT.NONE);
			lbl.setText("Text");
			textText = new Text(container, SWT.BORDER | SWT.MULTI);
			GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
			gd.heightHint = 128;
			textText.setLayoutData(gd);
			textText.setText(textString);
			
			if (accountId == null) {
				// set selected account for mandant
				IMandator selectedMandant =
					ContextServiceHolder.get().getActiveMandator().orElse(null);
				if (selectedMandant != null) {
					List<String> accounts = MailClientComponent.getMailClient().getAccounts();
					for (String string : accounts) {
						Optional<MailAccount> accountOptional =
							MailClientComponent.getMailClient().getAccount(string);
						if (accountOptional.isPresent()
							&& accountOptional.get().isForMandant(selectedMandant.getId())) {
							accountsViewer.setSelection(
								new StructuredSelection(accountOptional.get().getId()));
						}
					}
				}
			}
		}
		
		return area;
	}
	
	public void setAttachments(String attachments){
		this.attachments.setAttachments(attachments);
		getShell().layout(true, true);
	}
	
	public void setDocuments(String documents){
		this.attachments.setDocuments(documents);
		getShell().layout(true, true);
	}
	
	public void setTo(String to){
		if (to != null && !to.isEmpty()) {
			toString = to;
		}
	}
	
	public void setSubject(String subject){
		if (subject != null && !subject.isEmpty()) {
			subjectString = subject;
		}
	}
	
	public void setText(String text){
		if (text != null && !text.isEmpty()) {
			textString = text;
		}
	}
	
	private List<String> getSendMailAccounts(){
		List<String> ret = new ArrayList<String>();
		List<String> accounts = MailClientComponent.getMailClient().getAccounts();
		for (String accountId : accounts) {
			Optional<MailAccount> accountOptional =
				MailClientComponent.getMailClient().getAccount(accountId);
			if (accountOptional.isPresent()) {
				if (accountOptional.get().getType() == TYPE.SMTP) {
					ret.add(accountId);
				}
			}
		}
		return ret;
	}
	
	@Override
	protected void okPressed(){
		String validation = getValidation();
		if (validation != null) {
			setErrorMessage(validation);
			return;
		}
		super.okPressed();
	}
	
	@Override
	protected void createButtonsForButtonBar(Composite parent){
		Button outboxBtn = createButton(parent, -1, "in Oubox ablegen", false);
		super.createButtonsForButtonBar(parent);
		outboxBtn.setEnabled(
			!disableOutbox && createOutboxCommand != null && createOutboxCommand.isEnabled());
		outboxBtn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e){
				String validation = getValidation();
				if (validation != null) {
					setErrorMessage(validation);
					return;
				} else {
					setErrorMessage(null);
				}
				createOutboxElement();
			}
			
			private void createOutboxElement(){
				MailMessage message =
					new MailMessage().to(getTo()).cc(getCc()).subject(getSubject()).text(getText());
				message.setAttachments(attachments.getAttachments());
				message.setDocuments(attachments.getDocuments());
				Optional<ITaskDescriptor> descriptor =
					TaskUtil.createSendMailTaskDescriptor(account.getId(), message);
				// now try to call the create outbox command, is not part of core ...
				try {
					HashMap<String, String> params = new HashMap<String, String>();
					params.put("at.medevit.elexis.outbox.ui.command.createElementNoUi.dburi",
						StoreToStringServiceHolder.getStoreToString(descriptor.get()));
					ParameterizedCommand parametrizedCommmand =
						ParameterizedCommand.generateCommand(createOutboxCommand, params);
					PlatformUI.getWorkbench().getService(IHandlerService.class)
						.executeCommand(parametrizedCommmand, null);
				} catch (Exception ex) {
					LoggerFactory.getLogger(getClass())
						.warn("Create OutboxElement command not available");
				}
				// close dialog with cancel status, do not send mail
				cancelPressed();
			}
		});
	}
	
	private String getValidation(){
		StructuredSelection accountSelection = (StructuredSelection) accountsViewer.getSelection();
		if (accountSelection == null || accountSelection.isEmpty()) {
			return "Kein Konto ausgewählt.";
		}
		String accountId = (String) accountSelection.getFirstElement();
		Optional<MailAccount> optionalAccount =
			MailClientComponent.getMailClient().getAccount(accountId);
		if (!optionalAccount.isPresent()) {
			return "Kein Konto ausgewählt.";
		} else {
			account = optionalAccount.get();
		}
		
		String to = toText.getText();
		if(to == null || to.isEmpty()) {
			return "Keine an E-Mail Adresse.";
		}
		toString = to;
		
		ccString = ccText.getText();
		
		subjectString = subjectText.getText();
		
		textString = textText.getText();
		
		return null;
	}
	
	public String getTo(){
		return toString;
	}
	
	public String getCc(){
		return ccString;
	}
	
	public void setCc(String cc){
		this.ccString = cc;
	}
	
	public String getSubject(){
		return subjectString;
	}
	
	public String getText(){
		return textString;
	}
	
	public void setAccountId(String accountId){
		this.accountId = accountId;
	}
	
	public MailAccount getAccount(){
		return account;
	}
	
	public String getAttachmentsString(){
		return attachments.getAttachments();
	}
	
	public String getDocumentsString(){
		return attachments.getDocuments();
	}
	
	public void setMailMessage(MailMessage message){
		setTo(StringUtils.defaultString(message.getTo()));
		setCc(StringUtils.defaultString(message.getCc()));
		setSubject(StringUtils.defaultString(message.getSubject()));
		setText(StringUtils.defaultString(message.getText()));
		attachmentsString = message.getAttachmentsString();
		documentsString = message.getDocumentsString();
	}
	
	public void disableOutbox(){
		this.disableOutbox = true;
	}
	
	public void setDocumentsString(String documents){
		this.documentsString = documents;
	}
	
	public void setAttachmentsString(String attachments){
		this.attachmentsString = attachments;
	}
}
