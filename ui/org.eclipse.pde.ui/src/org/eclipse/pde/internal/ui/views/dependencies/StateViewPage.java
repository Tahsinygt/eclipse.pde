/*******************************************************************************
 * Copyright (c) 2007, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     EclipseSource Corporation - ongoing enhancements
 *******************************************************************************/
package org.eclipse.pde.internal.ui.views.dependencies;

import java.util.ArrayList;
import org.eclipse.jdt.ui.ISharedImages;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.action.*;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.*;
import org.eclipse.osgi.service.resolver.*;
import org.eclipse.osgi.util.NLS;
import org.eclipse.pde.core.plugin.PluginRegistry;
import org.eclipse.pde.internal.core.*;
import org.eclipse.pde.internal.ui.*;
import org.eclipse.pde.internal.ui.editor.plugin.ManifestEditor;
import org.eclipse.pde.internal.ui.elements.DefaultContentProvider;
import org.eclipse.pde.internal.ui.util.SharedLabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.dialogs.FilteredTree;
import org.eclipse.ui.dialogs.PatternFilter;
import org.eclipse.ui.part.Page;
import org.osgi.framework.Version;

public class StateViewPage extends Page implements IStateDeltaListener, IPluginModelListener {

	private IPropertyChangeListener fPropertyListener;
	private FilteredTree fFilteredTree = null;
	private TreeViewer fTreeViewer = null;
	private DependenciesView fView;
	private Composite fComposite;
	private Action fOpenAction;

	private static final String HIDE_RESOLVED = "hideResolved"; //$NON-NLS-1$
	private static final String SHOW_NONLEAF = "hideNonLeaf"; //$NON-NLS-1$

	private ViewerFilter fHideResolvedFilter = new ViewerFilter() {
		public boolean select(Viewer viewer, Object parentElement, Object element) {
			return ((element instanceof BundleDescription && !((BundleDescription) element).isResolved()) || parentElement instanceof BundleDescription && !((BundleDescription) parentElement).isResolved());
		}
	};

	private ViewerFilter fShowLeaves = new ViewerFilter() {
		public boolean select(Viewer viewer, Object parentElement, Object element) {
			if (element instanceof BundleDescription) {
				return ((BundleDescription) element).getDependents().length == 0;
			}
			return true;
		}
	};

	class FocusOnAction extends Action {
		public FocusOnAction(String text) {
			super(text);
			setDescription(PDEUIMessages.StateViewPage_focusActionDescription);
			setToolTipText(PDEUIMessages.StateViewPage_focusActionToolTip);
		}

		public void run() {
			setFocusOnSelection();
		}

	}

	class DependencyGroup {
		Object[] dependencies;

		public DependencyGroup(Object[] constraints) {
			dependencies = constraints;
		}

		public Object[] getChildren() {
			return dependencies;
		}

		public String toString() {
			return (dependencies[0] instanceof BundleSpecification) ? PDEUIMessages.StateViewPage_requiredBundles : PDEUIMessages.StateViewPage_importedPackages;
		}
	}

	class StateContentProvider extends DefaultContentProvider implements ITreeContentProvider {

		public Object[] getChildren(Object parentElement) {
			if (parentElement instanceof BundleDescription) {
				BundleDescription desc = (BundleDescription) parentElement;
				if (desc.isResolved()) {
					Object[] required = getResolvedDependencies(desc.getRequiredBundles());
					Object[] imported = getResolvedDependencies(desc.getImportPackages());
					ArrayList list = new ArrayList(2);
					if (required.length > 0)
						list.add(new DependencyGroup(required));
					if (imported.length > 0)
						list.add(new DependencyGroup(imported));
					return list.toArray();
				}
				return desc.getContainingState().getResolverErrors(desc);
			} else if (parentElement instanceof DependencyGroup) {
				return ((DependencyGroup) parentElement).getChildren();
			}
			return new Object[0];
		}

		public Object getParent(Object element) {
			return null;
		}

		public boolean hasChildren(Object element) {
			return getChildren(element).length > 0;
		}

		public Object[] getElements(Object inputElement) {
			if (inputElement instanceof State)
				return ((State) inputElement).getBundles();
			return new Object[0];
		}

		private Object[] getResolvedDependencies(VersionConstraint[] constraints) {
			ArrayList list = new ArrayList(constraints.length);
			for (int i = 0; i < constraints.length; i++)
				if (constraints[i].isResolved())
					list.add(constraints[i]);
			return list.toArray();
		}

	}

	class StateLabelProvider extends StyledCellLabelProvider {
		private PDELabelProvider fSharedProvider;

		public StateLabelProvider() {
			fSharedProvider = PDEPlugin.getDefault().getLabelProvider();
			fSharedProvider.connect(this);
		}

		public void dispose() {
			fSharedProvider.disconnect(this);
			super.dispose();
		}

		public void update(ViewerCell cell) {
			Object element = cell.getElement();
			StyledString styledString = new StyledString();
			if (element instanceof ImportPackageSpecification) {
				ImportPackageSpecification spec = (ImportPackageSpecification) element;
				styledString.append(spec.getName());
				ExportPackageDescription supplier = (ExportPackageDescription) spec.getSupplier();
				if (isJREPackage(supplier)) {
					styledString.append(PDEUIMessages.StateViewPage_suppliedByJRE);
				} else {
					element = supplier.getSupplier();
					styledString.append(PDEUIMessages.StateViewPage_suppliedBy);
				}
			} else if (element instanceof BundleSpecification) {
				styledString.append(((BundleSpecification) element).getSupplier().toString());
			} else if (element instanceof BundleDescription) {
				BundleDescription description = (BundleDescription) element;
				styledString.append(fSharedProvider.getObjectText(description));
				Version version = description.getVersion();
				// Bug 183417 - Bidi3.3: Elements' labels in the extensions page in the fragment manifest characters order is incorrect
				// Use the PDELabelProvider.formatVersion function to properly format the version for all languages including bidi
				styledString.append(' ').append(PDELabelProvider.formatVersion(version.toString())).toString();
				if (description.getLocation() != null) {
					styledString.append(" - " + description.getLocation(), StyledString.DECORATIONS_STYLER); //$NON-NLS-1$
				}
			} else {
				styledString.append(element.toString());
			}

			cell.setText(styledString.toString());
			cell.setStyleRanges(styledString.getStyleRanges());
			cell.setImage(getImage(element));
			super.update(cell);
		}

		public Image getImage(Object element) {
			if (element instanceof DependencyGroup)
				element = ((DependencyGroup) element).getChildren()[0];
			if (element instanceof BundleSpecification)
				element = ((BundleSpecification) element).getSupplier();
			if (element instanceof BundleDescription) {
				int flags = ((BundleDescription) element).isResolved() ? 0 : SharedLabelProvider.F_ERROR;
				return (((BundleDescription) element).getHost() == null) ? fSharedProvider.get(PDEPluginImages.DESC_PLUGIN_OBJ, flags) : fSharedProvider.get(PDEPluginImages.DESC_FRAGMENT_OBJ, flags);
			}
			if (element instanceof ImportPackageSpecification)
				return JavaUI.getSharedImages().getImage(ISharedImages.IMG_OBJS_PACKAGE);
			if (element instanceof ResolverError) {
				if (((ResolverError) element).getType() == ResolverError.PLATFORM_FILTER)
					return fSharedProvider.get(PDEPluginImages.DESC_OPERATING_SYSTEM_OBJ);
				return fSharedProvider.getImage(element);
			}
			return null;
		}

	}

	private boolean isJREPackage(ExportPackageDescription supplier) {
		// check for runtime's non-API directive.  This may change in the future
		return (((Integer) supplier.getDirective("x-equinox-ee")).intValue() > 0); //$NON-NLS-1$
	}

	public StateViewPage(DependenciesView view) {
		fView = view;
		fPropertyListener = new IPropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				String property = event.getProperty();
				if (property.equals(IPreferenceConstants.PROP_SHOW_OBJECTS)) {
					fTreeViewer.refresh();
				}
			}
		};
	}

	public void createControl(Composite parent) {
		fComposite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginHeight = layout.marginWidth = 0;
		fComposite.setLayout(layout);
		fComposite.setLayoutData(new GridData(GridData.FILL_BOTH));

		fFilteredTree = new FilteredTree(fComposite, SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL | SWT.SINGLE, new PatternFilter(), true) {
			protected void createControl(Composite parent, int treeStyle) {
				super.createControl(parent, treeStyle);

				// add 2px margin around filter text

				FormLayout layout = new FormLayout();
				layout.marginHeight = 0;
				layout.marginWidth = 0;
				setLayout(layout);

				FormData data = new FormData();
				data.top = new FormAttachment(0, 2);
				data.left = new FormAttachment(0, 2);
				data.right = new FormAttachment(100, -2);
				filterComposite.setLayoutData(data);

				data = new FormData();
				data.top = new FormAttachment(filterComposite, 2);
				data.left = new FormAttachment(0, 0);
				data.right = new FormAttachment(100, 0);
				data.bottom = new FormAttachment(100, 0);
				treeComposite.setLayoutData(data);
			}
		};

		fFilteredTree.setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND));

		fTreeViewer = fFilteredTree.getViewer();
		fTreeViewer.setContentProvider(new StateContentProvider());
		fTreeViewer.setLabelProvider(new StateLabelProvider());
		fTreeViewer.setComparator(DependenciesViewComparator.getViewerComparator());
		fTreeViewer.addDoubleClickListener(new IDoubleClickListener() {

			public void doubleClick(DoubleClickEvent event) {
				handleDoubleClick();
			}

		});

		if (getSettings().getBoolean(HIDE_RESOLVED))
			fTreeViewer.addFilter(fHideResolvedFilter);
		if (getSettings().getBoolean(SHOW_NONLEAF))
			fTreeViewer.addFilter(fShowLeaves);

		PDEPlugin.getDefault().getPreferenceStore().addPropertyChangeListener(fPropertyListener);
		getSite().setSelectionProvider(fTreeViewer);
		PDECore.getDefault().getModelManager().addStateDeltaListener(this);
	}

	public Control getControl() {
		return fComposite;
	}

	public void setFocus() {
		if (fFilteredTree != null) {
			Control c = fFilteredTree.getFilterControl();
			if (c != null && !c.isFocusControl()) {
				c.setFocus();
			}
		}
	}

	protected void handleDoubleClick() {
		StructuredSelection selection = (StructuredSelection) fTreeViewer.getSelection();
		if (selection.size() == 1) {
			BundleDescription desc = getBundleDescription(selection.getFirstElement());
			if (desc != null)
				ManifestEditor.openPluginEditor(desc.getSymbolicName());
		}
	}

	private BundleDescription getBundleDescription(Object obj) {
		if (obj instanceof BundleSpecification)
			obj = ((BundleSpecification) obj).getSupplier();
		else if (obj instanceof ImportPackageSpecification)
			obj = ((ExportPackageDescription) ((ImportPackageSpecification) obj).getSupplier()).getSupplier();
		if (obj instanceof BundleDescription)
			return (BundleDescription) obj;
		return null;
	}

	protected void setActive(boolean active) {
		if (active) {
			fView.updateTitle(PDEUIMessages.StateViewPage_title);
			State state = PDECore.getDefault().getModelManager().getState().getState();
			state.resolve(true);
			fTreeViewer.setInput(state);
			PDECore.getDefault().getModelManager().addPluginModelListener(this);
		} else {
			PDECore.getDefault().getModelManager().removePluginModelListener(this);
		}
	}

	public void makeContributions(IMenuManager menuManager, IToolBarManager toolBarManager, IStatusLineManager statusLineManager) {
		super.makeContributions(menuManager, toolBarManager, statusLineManager);
		Action filterResolved = new Action(PDEUIMessages.StateViewPage_showOnlyUnresolved_label, IAction.AS_CHECK_BOX) {
			public void run() {
				getSettings().put(HIDE_RESOLVED, isChecked());
				if (isChecked())
					fTreeViewer.addFilter(fHideResolvedFilter);
				else
					fTreeViewer.removeFilter(fHideResolvedFilter);
			}
		};
		Action filterLeaves = new Action(PDEUIMessages.StateViewPage_showLeaves, IAction.AS_CHECK_BOX) {
			public void run() {
				getSettings().put(SHOW_NONLEAF, isChecked());
				if (isChecked())
					fTreeViewer.addFilter(fShowLeaves);
				else
					fTreeViewer.removeFilter(fShowLeaves);
			}
		};

		filterResolved.setChecked(getSettings().getBoolean(HIDE_RESOLVED));
		filterLeaves.setChecked(getSettings().getBoolean(SHOW_NONLEAF));
		menuManager.add(filterResolved);
		menuManager.add(filterLeaves);

		Action action = new FocusOnAction(PDEUIMessages.StateViewPage_focusOnTitle);
		action.setImageDescriptor(PDEPluginImages.DESC_FOCUS_ON);
		if (toolBarManager.find(DependenciesView.TREE_ACTION_GROUP) != null)
			toolBarManager.prependToGroup(DependenciesView.TREE_ACTION_GROUP, action);
		else
			toolBarManager.add(action);

		hookContextMenu();
	}

	private void hookContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu"); //$NON-NLS-1$
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				fillContextMenu(manager);
			}
		});
		Menu menu = menuMgr.createContextMenu(fTreeViewer.getControl());
		fTreeViewer.getControl().setMenu(menu);

		getSite().registerContextMenu(fView.getSite().getId(), menuMgr, fTreeViewer);
	}

	private void fillContextMenu(IMenuManager menu) {
		IStructuredSelection selection = (IStructuredSelection) fTreeViewer.getSelection();
		BundleDescription desc = getBundleDescription(selection.getFirstElement());
		if (desc != null) {
			if (fOpenAction == null) {
				fOpenAction = new Action(PDEUIMessages.StateViewPage_openItem) {
					public void run() {
						handleDoubleClick();
					}
				};
			}
			menu.add(fOpenAction);
			menu.add(new Separator());
			String name = ((LabelProvider) fTreeViewer.getLabelProvider()).getText(desc);
			menu.add(new FocusOnAction(NLS.bind(PDEUIMessages.StateViewPage_focusOnSelection, name)));

			menu.add(new Separator());
			menu.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
		}
	}

	public void dispose() {
		PDECore.getDefault().getModelManager().removeStateDeltaListener(this);
		super.dispose();
	}

	public void stateResolved(final StateDelta delta) {
		if (!fView.getCurrentPage().equals(this) || fTreeViewer == null || fTreeViewer.getTree().isDisposed())
			// if this page is not active, then wait until we call refresh on next activation
			return;
		fTreeViewer.getTree().getDisplay().asyncExec(new Runnable() {
			public void run() {
				if (delta == null) {
					fTreeViewer.refresh();
				} else {
					BundleDelta[] deltas = delta.getChanges();
					for (int i = 0; i < deltas.length; i++) {
						int type = deltas[i].getType();
						if (type == BundleDelta.REMOVED || type == BundleDelta.RESOLVED || type == BundleDelta.ADDED || type == BundleDelta.UNRESOLVED) {
							fTreeViewer.refresh();
							break;
						}
					}
				}
			}
		});
	}

	public void stateChanged(final State newState) {
		if (!fView.getCurrentPage().equals(this) || fTreeViewer == null || fTreeViewer.getTree().isDisposed())
			// if this page is not active, then wait until we call refresh on next activation
			return;
		fTreeViewer.getTree().getDisplay().asyncExec(new Runnable() {
			public void run() {
				fTreeViewer.setInput(newState);
			}
		});
	}

	// Changes State view to dependencies view and sets the input as the corresponding selected item in the tree viewer.
	private void setFocusOnSelection() {
		// first, find the Show State Action from the toolbar
		IContributionItem item = getSite().getActionBars().getToolBarManager().find(DependenciesView.SHOW_STATE_ACTION_ID);
		if (item != null && item instanceof ActionContributionItem) {
			// then get selection item
			IStructuredSelection selection = (IStructuredSelection) fTreeViewer.getSelection();
			if (selection.isEmpty())
				return;
			BundleDescription desc = getBundleDescription(selection.getFirstElement());
			if (desc != null) {
				IAction action = ((ActionContributionItem) item).getAction();
				// deselect the action to show the state
				action.setChecked(false);
				// run the action to change the view back to traditional dependencies view
				action.run();
				// set the traditional view to focus on selected object from state view
				fView.openTo(PluginRegistry.findModel(desc));
			}
		}
	}

	private IDialogSettings getSettings() {
		IDialogSettings master = PDEPlugin.getDefault().getDialogSettings();
		IDialogSettings section = master.getSection("dependenciesView"); //$NON-NLS-1$
		if (section == null) {
			section = master.addNewSection("dependenciesView"); //$NON-NLS-1$
		}
		return section;
	}

	public void modelsChanged(PluginModelDelta delta) {
		if (fTreeViewer == null || fTreeViewer.getTree().isDisposed())
			return;
		if (delta.getAddedEntries().length > 0 || delta.getChangedEntries().length > 0 || delta.getRemovedEntries().length > 0)
			fTreeViewer.getTree().getDisplay().asyncExec(new Runnable() {
				public void run() {
					fTreeViewer.refresh();
				}
			});
	}

}
