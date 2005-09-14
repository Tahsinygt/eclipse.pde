/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.editor.product;

import java.util.TreeSet;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.pde.core.plugin.IPluginElement;
import org.eclipse.pde.core.plugin.IPluginExtension;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.core.plugin.IPluginObject;
import org.eclipse.pde.internal.core.PDECore;
import org.eclipse.pde.internal.core.iproduct.IIntroInfo;
import org.eclipse.pde.internal.core.iproduct.IProduct;
import org.eclipse.pde.internal.core.iproduct.IProductModel;
import org.eclipse.pde.internal.core.iproduct.IProductModelFactory;
import org.eclipse.pde.internal.core.iproduct.IProductPlugin;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.pde.internal.ui.PDEUIMessages;
import org.eclipse.pde.internal.ui.editor.PDEFormPage;
import org.eclipse.pde.internal.ui.editor.PDESection;
import org.eclipse.pde.internal.ui.model.bundle.Bundle;
import org.eclipse.pde.internal.ui.model.bundle.BundleModel;
import org.eclipse.pde.internal.ui.model.bundle.ManifestHeader;
import org.eclipse.pde.internal.ui.parts.ComboPart;
import org.eclipse.pde.internal.ui.wizards.product.ProductIntroWizard;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.ui.forms.FormColors;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.forms.widgets.TableWrapData;
import org.eclipse.ui.forms.widgets.TableWrapLayout;
import org.osgi.framework.Constants;


public class IntroSection extends PDESection {

	private ComboPart fIntroCombo;
	private IResource fManifest;
	private String[] fAvailableIntroIds;
	private final static String INTRO_POINT = "org.eclipse.ui.intro"; //$NON-NLS-1$

	public IntroSection(PDEFormPage page, Composite parent) {
		super(page, parent, Section.DESCRIPTION);
		createClient(getSection(), page.getEditor().getToolkit());
	}
	
	public void createClient(Section section, FormToolkit toolkit) {
		section.setText(PDEUIMessages.IntroSection_sectionText); 
		section.setDescription(PDEUIMessages.IntroSection_sectionDescription); 
		
		Composite client = toolkit.createComposite(section);
		TableWrapLayout layout = new TableWrapLayout();
		layout.numColumns = 3;
		layout.topMargin = 5;
		client.setLayout(layout);
		
		
		Label label = toolkit.createLabel(client, PDEUIMessages.IntroSection_introLabel, SWT.WRAP);
		TableWrapData td = new TableWrapData();
		td.colspan = 3;
		label.setLayoutData(td);
		
		Label introLabel = toolkit.createLabel(client, PDEUIMessages.IntroSection_introInput); 
		introLabel.setForeground(toolkit.getColors().getColor(FormColors.TITLE));
		td = new TableWrapData();
		td.valign = TableWrapData.MIDDLE;
		introLabel.setLayoutData(td);
		
		fIntroCombo = new ComboPart();
		fIntroCombo.createControl(client, toolkit, SWT.READ_ONLY);
		td = new TableWrapData(TableWrapData.FILL_GRAB);
		td.valign = TableWrapData.MIDDLE;
		fIntroCombo.getControl().setLayoutData(td);
		loadManifestAndIntroIds(false);
		if (fAvailableIntroIds != null ) fIntroCombo.setItems(fAvailableIntroIds);
		fIntroCombo.add(""); //$NON-NLS-1$
		fIntroCombo.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				handleSelection();
			}
		});
		
		Button button = toolkit.createButton(client, PDEUIMessages.IntroSection_new, SWT.PUSH); 
		button.setEnabled(isEditable());
		button.setLayoutData(new TableWrapData(TableWrapData.FILL));
		button.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				handleNewIntro();
			}
		});	
		fIntroCombo.getControl().setEnabled(isEditable());
		
		toolkit.paintBordersFor(client);
		section.setClient(client);
		section.setLayoutData(new GridData(GridData.FILL_HORIZONTAL|GridData.VERTICAL_ALIGN_BEGINNING));
	}
	
	private void handleSelection() {
		if (!productDefined()) {
			fIntroCombo.setText(""); //$NON-NLS-1$
			return;
		}
		getIntroInfo().setId(fIntroCombo.getSelection());
		try { addDependenciesAndPlugins(); } catch (CoreException e) {}
	}

	private void loadManifestAndIntroIds(boolean onlyLoadManifest) {
		TreeSet result = new TreeSet();
		String introId;
		IPluginModelBase[] plugins = PDECore.getDefault().getModelManager().getPlugins();
		for (int i = 0; i < plugins.length; i++) {
			IPluginExtension[] extensions = plugins[i].getPluginBase().getExtensions();
			for (int j = 0; j < extensions.length; j++) {
				String point = extensions[j].getPoint();
				if (point != null && point.equals("org.eclipse.ui.intro")) {//$NON-NLS-1$
					IPluginObject[] children = extensions[j].getChildren();
					for (int k = 0; k < children.length; k++) {
						IPluginElement element = (IPluginElement)children[k];
						if ("introProductBinding".equals(element.getName())) {//$NON-NLS-1$
							if (element.getAttribute("productId").getValue().equals(getProduct().getId())) { //$NON-NLS-1$
								if (fManifest == null)
									fManifest = element.getPluginModel().getUnderlyingResource();
								if (onlyLoadManifest)
									return;
								introId = element.getAttribute("introId").getValue(); //$NON-NLS-1$
								if (introId != null)
									result.add(introId);
							}
						}
					}
				}
			}
		}
		fAvailableIntroIds = (String[])result.toArray(new String[result.size()]);
	}
	
	private void handleNewIntro() {
		boolean needNewProduct = false;
		if (!productDefined()) {
			needNewProduct = true;
			MessageDialog mdiag = new MessageDialog(PDEPlugin.getActiveWorkbenchShell(),
					PDEUIMessages.IntroSection_undefinedProductId, null, 
					PDEUIMessages.IntroSection_undefinedProductIdMessage,
					MessageDialog.QUESTION, new String[] {IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL }, 0);
	        if (mdiag.open() != MessageDialog.OK)
	        	return;
		}
		ProductIntroWizard wizard = new ProductIntroWizard(getProduct(), needNewProduct);
		WizardDialog dialog = new WizardDialog(PDEPlugin.getActiveWorkbenchShell(), wizard);
		dialog.create();
		if (dialog.open() == WizardDialog.OK) {
			String id = wizard.getIntroId();
			fIntroCombo.add(id, 0);
			fIntroCombo.setText(id);
			getIntroInfo().setId(id);
			try { addDependenciesAndPlugins(); } catch (CoreException e) {}
		}
	}

	public void refresh() {
		String introId = getIntroInfo().getId();
		if (introId != null) fIntroCombo.setText(introId);
		super.refresh();
	}
	
	private IIntroInfo getIntroInfo() {
		IIntroInfo info = getProduct().getIntroInfo();
		if (info == null) {
			info = getModel().getFactory().createIntroInfo();
			getProduct().setIntroInfo(info);
		}
		return info;
	}
	
	private IProduct getProduct() {
		return getModel().getProduct();
	}
	
	private IProductModel getModel() {
		return (IProductModel)getPage().getPDEEditor().getAggregateModel();
	}
	
	private boolean productDefined() {
		return !getProduct().getId().equals(""); //$NON-NLS-1$
	}
	
	private void addDependenciesAndPlugins() throws CoreException {
		IProduct product = getProduct();
		IProductModelFactory factory = product.getModel().getFactory();
		IProductPlugin plugin = factory.createPlugin();
		plugin.setId(INTRO_POINT);
		product.addPlugin(plugin);
		
		PluginSection.handleAddRequired(new IProductPlugin[] {plugin});
		if (fManifest == null) loadManifestAndIntroIds(true);
		if (fManifest != null) addRequiredBundle();
	}
	
	private void addRequiredBundle() throws CoreException {
		ITextFileBufferManager manager = FileBuffers.getTextFileBufferManager();
		IPath manifestPath = fManifest.getFullPath();
		IProgressMonitor monitor = new NullProgressMonitor();
		try {
			manager.connect(manifestPath, monitor);
			ITextFileBuffer buffer = manager.getTextFileBuffer(manifestPath);

			IDocument document = buffer.getDocument();
			
			String ld = TextUtilities.getDefaultLineDelimiter(document);
			TextEdit edit = checkTrailingNewline(document, ld);
			if (edit != null)
				edit.apply(document);
			
			BundleModel model = new BundleModel(document, false);
			model.load();
			if (!model.isLoaded())
				return;
			Bundle bundle = (Bundle)model.getBundle();
			edit = createAddToHeaderTextEdit(document, bundle, Constants.REQUIRE_BUNDLE, ld);
			if (edit != null) {
				edit.apply(document);
				buffer.commit(monitor, true);
			}
		} catch (MalformedTreeException e) {
		} catch (BadLocationException e) {
		} finally {
			manager.disconnect(manifestPath, monitor);
		}
	}
	
	private TextEdit createAddToHeaderTextEdit(IDocument doc, Bundle bundle, String headerName, String ld) {
		ManifestHeader header = bundle.getManifestHeader(headerName);
		if (header == null) 
			return new InsertEdit(doc.getLength(), Constants.REQUIRE_BUNDLE + ": " + INTRO_POINT + ld); //$NON-NLS-1$
		if (header.getValue().indexOf(INTRO_POINT) == -1)
			return new InsertEdit(header.getOffset() + header.getLength() - 1, "," + ld + " " + INTRO_POINT); //$NON-NLS-1$ //$NON-NLS-2$
		return null;
	}
	
	private TextEdit checkTrailingNewline(IDocument document, String ld) {
		try {
			int len = ld.length();
			if (!document.get(document.getLength() - len, len).equals(ld)) {
				return new InsertEdit(document.getLength(), ld);
			}
		} catch (BadLocationException e) {
		}
		return null;
	}
	
}
