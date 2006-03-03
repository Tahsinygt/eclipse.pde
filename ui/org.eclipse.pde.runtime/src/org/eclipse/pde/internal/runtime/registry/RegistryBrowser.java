/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.runtime.registry;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionDelta;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IRegistryChangeEvent;
import org.eclipse.core.runtime.IRegistryChangeListener;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.pde.internal.runtime.IHelpContextIds;
import org.eclipse.pde.internal.runtime.PDERuntimeMessages;
import org.eclipse.pde.internal.runtime.PDERuntimePlugin;
import org.eclipse.pde.internal.runtime.PDERuntimePluginImages;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.XMLMemento;
import org.eclipse.ui.part.DrillDownAdapter;
import org.eclipse.ui.part.ViewPart;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
public class RegistryBrowser extends ViewPart implements BundleListener, IRegistryChangeListener {
	
	public static final String SHOW_RUNNING_PLUGINS = "RegistryView.showRunning.label"; //$NON-NLS-1$
//	public static final String REGISTRY_ORIENTATION = "RegistryView.orientation"; //$NON-NLS-1$
//	public static final int VERTICAL_ORIENTATION = 1;
//	public static final int HORIZONTAL_ORIENTATION = 2;
//	public static final int SINGLE_PANE_ORIENTATION = 3;
	
	private TreeViewer treeViewer;
	private IMemento memento;
//	private static int orientation;
//	private int[] horizontalSashWeight;
//	private int[] verticalSashWeight;
//	private static final int[] DEFAULT_SASH_WEIGHTS = {13, 6};
	
	// menus and action items
	private Action refreshAction;
	private Action showPluginsAction;
	private Action collapseAllAction;
//	private Action[] toggleViewAction;
	private DrillDownAdapter drillDownAdapter;
	
	//attributes view
//	private SashForm fSashForm;
//	private PropertySheetPage fPropertySheet;
	
	// single-pane control
	private Composite mainView;
	
	/*
	 * customized DrillDownAdapter which modifies enabled state of showing active/inactive
	 * plug-ins action - see Bug 58467
	 */
	class RegistryDrillDownAdapter extends DrillDownAdapter{
		public RegistryDrillDownAdapter(TreeViewer tree){
			super(tree);
		}

		public void goInto() {
			super.goInto();
			showPluginsAction.setEnabled(!canGoHome());
		}

		public void goBack() {
			super.goBack();
			showPluginsAction.setEnabled(!canGoHome());
		}

		public void goHome() {
			super.goHome();
			showPluginsAction.setEnabled(!canGoHome());
		}

		public void goInto(Object newInput) {
			super.goInto(newInput);
			showPluginsAction.setEnabled(!canGoHome());
		}
	}
	public RegistryBrowser() {
		super();
	}
	
	public void init(IViewSite site, IMemento memento) throws PartInitException {
		super.init(site, memento);
		if (memento == null)
			this.memento = XMLMemento.createWriteRoot("REGISTRYVIEW"); //$NON-NLS-1$
		else
			this.memento = memento;
		initializeMemento();
//		orientation = this.memento.getInteger(REGISTRY_ORIENTATION).intValue();
	}
	
	private void initializeMemento() {
		// show all plug-ins by default (i.e. not just activated ones)
		if (memento.getString(SHOW_RUNNING_PLUGINS) == null)
			memento.putString(SHOW_RUNNING_PLUGINS, "false"); //$NON-NLS-1$
//		if (memento.getInteger(REGISTRY_ORIENTATION) == null)
//			memento.putInteger(REGISTRY_ORIENTATION, HORIZONTAL_ORIENTATION);
	}
	
	public void dispose() {
		Platform.getExtensionRegistry().removeRegistryChangeListener(this);
		PDERuntimePlugin.getDefault().getBundleContext().removeBundleListener(
				this);
		super.dispose();
	}
	
	public void createPartControl(Composite parent) {
		// create the sash form that will contain the tree viewer & text viewer
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginHeight = layout.marginWidth = 0;
		composite.setLayout(layout);
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));
		makeActions();
		createTreeViewer(composite);
//		createAttributesViewer();
		fillToolBar();
		treeViewer.refresh();
//		setViewOrientation(orientation);
		setContentDescription(((RegistryBrowserContentProvider)treeViewer.getContentProvider()).getTitleSummary());
		
		Platform.getExtensionRegistry().addRegistryChangeListener(this);
		PDERuntimePlugin.getDefault().getBundleContext().addBundleListener(this);
	}
	private void createTreeViewer(Composite parent) {
		mainView = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginHeight = layout.marginWidth = 0;
		mainView.setLayout(layout);
		mainView.setLayoutData(new GridData(GridData.FILL_BOTH));	
		
		Tree tree = new Tree(mainView, SWT.FLAT);
		GridData gd = new GridData(GridData.FILL_BOTH);
		tree.setLayoutData(gd);
		treeViewer = new TreeViewer(tree);
		boolean showRunning = memento.getString(SHOW_RUNNING_PLUGINS).equals("true") ? true : false; //$NON-NLS-1$
		treeViewer.setContentProvider(new RegistryBrowserContentProvider(treeViewer, showRunning));
		treeViewer.setLabelProvider(new RegistryBrowserLabelProvider(treeViewer));
		treeViewer.setUseHashlookup(true);
		treeViewer.setSorter(new ViewerSorter(){
			public int compare(Viewer viewer, Object e1, Object e2) {
				if (e1 instanceof IBundleFolder && e2 instanceof IBundleFolder)
					return ((IBundleFolder)e1).getFolderId() - ((IBundleFolder)e2).getFolderId();
				return super.compare(viewer, e1, e2);
			}
		});
		
		Bundle[] bundles = PDERuntimePlugin.getDefault().getBundleContext().getBundles();
		treeViewer.setInput(new PluginObjectAdapter(bundles));
		
//		treeViewer.addSelectionChangedListener(new ISelectionChangedListener() {
//			public void selectionChanged(SelectionChangedEvent event) {
//				Object selection = ((IStructuredSelection) event.getSelection())
//				.getFirstElement();
//				updateAttributesView(selection);
//			}
//		});
//		treeViewer.addDoubleClickListener(new IDoubleClickListener() {
//			public void doubleClick(DoubleClickEvent event) {
//				Object selection = ((IStructuredSelection) event.getSelection())
//				.getFirstElement();
//				updateAttributesView(selection);
//				if (selection != null && treeViewer.isExpandable(selection))
//					treeViewer.setExpandedState(selection, !treeViewer
//							.getExpandedState(selection));
//			}
//		});
		
		PlatformUI.getWorkbench().getHelpSystem().setHelp(treeViewer.getControl(),
				IHelpContextIds.REGISTRY_VIEW);
		
		getViewSite().setSelectionProvider(treeViewer);
		
		MenuManager popupMenuManager = new MenuManager();
		IMenuListener listener = new IMenuListener() {
			public void menuAboutToShow(IMenuManager mng) {
				fillContextMenu(mng);
			}
		};
		popupMenuManager.setRemoveAllWhenShown(true);
		popupMenuManager.addMenuListener(listener);
		Menu menu = popupMenuManager.createContextMenu(tree);
		tree.setMenu(menu);
	}
	

//	protected void createAttributesViewer() {
//		Composite composite = new Composite(getSashForm(), SWT.FLAT);
//		GridLayout layout = new GridLayout();
//		layout.marginWidth = layout.marginHeight = 0;
//		layout.numColumns = 2;
//		layout.makeColumnsEqualWidth = false;
//		composite.setLayout(layout);
//		composite.setLayoutData(new GridData(GridData.FILL_BOTH));
//		
//		createPropertySheet(composite);
//	}	
//
//	
//	protected void createPropertySheet(Composite parent) {
//		Composite composite = new Composite(parent, SWT.NONE);
//		GridLayout layout = new GridLayout();
//		layout.marginWidth = layout.marginHeight = 0;
//		composite.setLayout(layout);
//		GridData gd = new GridData(GridData.FILL_BOTH);
//		gd.horizontalSpan = 2;
//		composite.setLayoutData(gd);
//		fPropertySheet = new PropertySheetPage();
//		fPropertySheet.createControl(composite);
//		gd = new GridData(GridData.FILL_BOTH);
//		fPropertySheet.getControl().setLayoutData(gd);
//		fPropertySheet.makeContributions(new MenuManager(),
//				new ToolBarManager(), null);
//	}
	
	private void fillToolBar(){
		drillDownAdapter = new RegistryDrillDownAdapter(treeViewer);
		IActionBars bars = getViewSite().getActionBars();
		IToolBarManager mng = bars.getToolBarManager();
		drillDownAdapter.addNavigationActions(mng);
		mng.add(refreshAction);
		mng.add(new Separator());
		mng.add(collapseAllAction);
		IMenuManager mgr = bars.getMenuManager();
//		mgr.add(toggleViewAction[0]);
//		mgr.add(toggleViewAction[1]);
//		mgr.add(toggleViewAction[2]);
		mgr.add(new Separator());
		mgr.add(showPluginsAction);
	}
	public void fillContextMenu(IMenuManager manager) {
		manager.add(refreshAction);
		manager.add(new Separator());
		drillDownAdapter.addNavigationActions(manager);
		manager.add(new Separator());
		manager.add(showPluginsAction);
	}
	public TreeViewer getTreeViewer() {
		return treeViewer;
	}
	
//	protected SashForm getSashForm() {
//		return fSashForm;
//	}
	
	public void saveState(IMemento memento) {
		if (memento == null || this.memento == null || treeViewer == null)
			return;
		boolean showRunning = ((RegistryBrowserContentProvider) treeViewer
				.getContentProvider()).isShowRunning();
		if (showRunning)
			this.memento.putString(SHOW_RUNNING_PLUGINS, Boolean.toString(true));
		else
			this.memento.putString(SHOW_RUNNING_PLUGINS, Boolean.toString(false));
//		this.memento.putInteger(REGISTRY_ORIENTATION, orientation);
		memento.putMemento(this.memento);
	}
	
	
//	public void updateAttributesView(Object selection) {
//		if (selection != null)
//			fPropertySheet.selectionChanged(null, new StructuredSelection(
//					selection));
//		else
//			fPropertySheet.selectionChanged(null, new StructuredSelection(
//					new Object()));
//	}
//	
//	private void setSashForm(SashForm sashForm) {
//		fSashForm = sashForm;
//	}
	
	public void setFocus() {
		treeViewer.getTree().setFocus();
	}
	
	/*
	 * @see org.osgi.framework.BundleListener#bundleChanged(org.osgi.framework.BundleEvent)
	 */
	public void bundleChanged(BundleEvent event) {
		if (treeViewer == null)
			return;
		Tree tree = treeViewer.getTree();
		if (tree.isDisposed())
			return;
		
		final RegistryBrowserContentProvider provider = ((RegistryBrowserContentProvider) treeViewer.getContentProvider());
		final Bundle eventBundle = Platform.getBundle(event.getBundle().getSymbolicName());
		if (eventBundle == null)
			return;
		final PluginObjectAdapter adapter = new PluginObjectAdapter(eventBundle);
		tree.getDisplay().asyncExec(new Runnable() {
			public void run() {
				if (treeViewer == null || treeViewer.getTree() == null || treeViewer.getTree().isDisposed())
					return;
				TreeItem[] items = treeViewer.getTree().getItems();
				if (items != null) {
					for (int i = 0; i < items.length; i++) {
						PluginObjectAdapter plugin = (PluginObjectAdapter) items[i].getData();
						if (plugin != null) {
							Object object = plugin.getObject();
							if (object instanceof Bundle) {
								Bundle bundle = (Bundle) object;
								if (bundle.equals(eventBundle)) {
									treeViewer.remove(plugin);
									break;
								}
							}
						}
					}
				}
				if (provider.isShowRunning() && eventBundle.getState() != Bundle.ACTIVE)
					return;
				treeViewer.add(treeViewer.getInput(), adapter);
				updateTitle();
			}
		});
	}
	/*
	 * @see org.eclipse.core.runtime.IRegistryChangeListener#registryChanged(org.eclipse.core.runtime.IRegistryChangeEvent)
	 */
	public void registryChanged(IRegistryChangeEvent event) {
		final IExtensionDelta[] deltas = event.getExtensionDeltas();
		treeViewer.getTree().getDisplay().syncExec(new Runnable() {
			public void run() {
				for (int i = 0; i < deltas.length; i++) {
					IExtension ext = deltas[i].getExtension();
					IExtensionPoint extPoint = deltas[i].getExtensionPoint();
					Bundle bundle = Platform.getBundle(ext.getNamespaceIdentifier());
					if (bundle == null)
						continue;
					PluginObjectAdapter adapter = new PluginObjectAdapter(bundle);
					if (deltas[i].getKind() == IExtensionDelta.ADDED) {
						if (ext != null)
							treeViewer.add(adapter, ext);
						if (extPoint != null)
							treeViewer.add(adapter, extPoint);
					} else {
						if (ext != null)
							treeViewer.remove(ext);
						if (extPoint != null)
							treeViewer.remove(extPoint);
						treeViewer.refresh();
					}
				}
				updateTitle();
			}
		});
	}
	/*
	 * toolbar and context menu actions
	 */
	public void makeActions() {
		refreshAction = new Action("refresh") { //$NON-NLS-1$
			public void run() {
				BusyIndicator.showWhile(treeViewer.getTree().getDisplay(),
						new Runnable() {
					public void run() {
						treeViewer.refresh();
					}
				});
			}
		};
		refreshAction.setText(PDERuntimeMessages.RegistryView_refresh_label);
		refreshAction.setToolTipText(PDERuntimeMessages.RegistryView_refresh_tooltip);
		refreshAction.setImageDescriptor(PDERuntimePluginImages.DESC_REFRESH);
		refreshAction.setDisabledImageDescriptor(PDERuntimePluginImages.DESC_REFRESH_DISABLED);
		
		showPluginsAction = new Action(PDERuntimeMessages.RegistryView_showRunning_label){
			public void run() {
				RegistryBrowserContentProvider cp = (RegistryBrowserContentProvider) treeViewer.getContentProvider();
				cp.setShowRunning(showPluginsAction.isChecked());
				treeViewer.refresh();
				updateTitle();
			}
		};
		showPluginsAction.setChecked(memento.getString(SHOW_RUNNING_PLUGINS).equals("true")); //$NON-NLS-1$
		
		collapseAllAction = new Action("collapseAll"){ //$NON-NLS-1$
			public void run(){
				treeViewer.collapseAll();
			}
		};
		collapseAllAction.setText(PDERuntimeMessages.RegistryView_collapseAll_label);
		collapseAllAction.setImageDescriptor(PDERuntimePluginImages.DESC_COLLAPSE_ALL);
		collapseAllAction.setToolTipText(PDERuntimeMessages.RegistryView_collapseAll_tooltip);
		
//		toggleViewAction = new TogglePropertiesAction[3];
//		toggleViewAction[0] = new TogglePropertiesAction(this, VERTICAL_ORIENTATION);
//		toggleViewAction[1] = new TogglePropertiesAction(this, HORIZONTAL_ORIENTATION);
//		toggleViewAction[2] = new TogglePropertiesAction(this, SINGLE_PANE_ORIENTATION);
//		if (orientation == VERTICAL_ORIENTATION)
//			toggleViewAction[0].setChecked(true);
//		else if (orientation == HORIZONTAL_ORIENTATION)
//			toggleViewAction[1].setChecked(true);
//		else
//			toggleViewAction[2].setChecked(true);
	}

	
//	protected void setLastSashWeights(int[] weights) {
//		if (orientation == HORIZONTAL_ORIENTATION)
//			horizontalSashWeight = weights;
//		else if (orientation == VERTICAL_ORIENTATION)
//			verticalSashWeight = weights;
//	}
//	
//	public void setViewOrientation(int viewOrientation){
//		setLastSashWeights(getSashForm().getWeights());
//		if (viewOrientation == SINGLE_PANE_ORIENTATION){
//			getSashForm().setMaximizedControl(mainView);
//		} else {
//			if (viewOrientation == VERTICAL_ORIENTATION)
//				getSashForm().setOrientation(SWT.VERTICAL);
//			else
//				getSashForm().setOrientation(SWT.HORIZONTAL);
//			getSashForm().setMaximizedControl(null);
//			getSashForm().setWeights(getLastSashWeights(viewOrientation));
//		}
//		orientation = viewOrientation;
//	}
//	protected int[] getLastSashWeights(int viewOrientation) {
//		if (viewOrientation == HORIZONTAL_ORIENTATION){
//			if (horizontalSashWeight == null) 
//				horizontalSashWeight = DEFAULT_SASH_WEIGHTS;
//			return horizontalSashWeight;
//		} 
//		if (verticalSashWeight == null)
//			verticalSashWeight = DEFAULT_SASH_WEIGHTS;
//		return verticalSashWeight;
//	}
	
	public void updateTitle(){
		if (treeViewer == null || treeViewer.getContentProvider() == null)
			return;
		setContentDescription(((RegistryBrowserContentProvider)treeViewer.getContentProvider()).getTitleSummary());
	}
}
