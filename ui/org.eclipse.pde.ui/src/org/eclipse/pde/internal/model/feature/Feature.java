package org.eclipse.pde.internal.model.feature;
/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import org.eclipse.pde.internal.*;
import org.eclipse.pde.model.plugin.*;
import org.eclipse.pde.model.*;

import java.io.*;
import org.w3c.dom.*;
import org.eclipse.core.runtime.*;
import org.eclipse.pde.internal.base.model.feature.*;
import java.util.*;
import org.eclipse.pde.internal.base.model.*;

public class Feature extends VersionableObject implements IFeature {
	final static String INDENT = "   ";
	private String providerName;
	private IFeatureURL url;
	private IFeatureInfo[] infos = new IFeatureInfo[3];
	private Vector data = new Vector();
	private Vector plugins = new Vector();
	private Vector imports = new Vector();
	private String os;
	private String ws;
	private String nl;
	private String arch;
	private IFeatureInstallHandler handler;
	private boolean primary;

	public void addPlugins(IFeaturePlugin[] newPlugins) throws CoreException {
		ensureModelEditable();
		for (int i = 0; i < newPlugins.length; i++) {
			plugins.add(newPlugins[i]);
		}
		fireStructureChanged(newPlugins, IModelChangedEvent.INSERT);
	}

	public void addData(IFeatureData[] newData) throws CoreException {
		ensureModelEditable();
		for (int i = 0; i < newData.length; i++) {
			data.add(newData[i]);
		}
		fireStructureChanged(newData, IModelChangedEvent.INSERT);
	}

	public void addImport(IFeatureImport iimport) throws CoreException {
		ensureModelEditable();
		imports.add(iimport);
		fireStructureChanged(iimport, IModelChangedEvent.INSERT);
	}

	public IFeaturePlugin[] getPlugins() {
		IFeaturePlugin[] result = new IFeaturePlugin[plugins.size()];
		plugins.copyInto(result);
		return result;
	}

	public IFeatureData[] getData() {
		IFeatureData[] result = new IFeatureData[data.size()];
		data.copyInto(result);
		return result;
	}
	public IFeatureImport[] getImports() {
		IFeatureImport[] result = new IFeatureImport[imports.size()];
		imports.copyInto(result);
		return result;
	}
	public String getProviderName() {
		return providerName;
	}

	public IPluginModelBase getReferencedModel(IFeaturePlugin reference) {
		WorkspaceModelManager mng = PDEPlugin.getDefault().getWorkspaceModelManager();
		IPluginModelBase[] models = null;
		if (reference.isFragment())
			models = mng.getWorkspaceFragmentModels();
		else
			models = mng.getWorkspacePluginModels();

		for (int i = 0; i < models.length; i++) {
			IPluginBase base = models[i].getPluginBase();
			if (base.getId().equals(reference.getId()))
				return models[i];
		}
		return null;
	}
	public IFeatureURL getURL() {
		return url;
	}
	public IFeatureInstallHandler getInstallHandler() {
		return handler;
	}

	public boolean isPrimary() {
		return primary;
	}

	public void setPrimary(boolean newValue) throws CoreException {
		if (this.primary == newValue)
			return;
		ensureModelEditable();
		Boolean oldValue = this.primary ? Boolean.TRUE : Boolean.FALSE;
		this.primary = newValue;
		firePropertyChanged(
			P_PRIMARY,
			oldValue,
			newValue ? Boolean.TRUE : Boolean.FALSE);
	}

	protected void parse(Node node) {
		super.parse(node);
		providerName = getNodeAttribute(node, "provider-name");
		os = getNodeAttribute(node, "os");
		ws = getNodeAttribute(node, "ws");
		nl = getNodeAttribute(node, "nl");
		NodeList children = node.getChildNodes();

		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				String tag = child.getNodeName().toLowerCase();
				if (tag.equals("description")) {
					IFeatureInfo info =
						getModel().getFactory().createInfo(IFeature.INFO_DESCRIPTION);
					((FeatureInfo) info).parse(child);
					infos[IFeature.INFO_DESCRIPTION] = info;
				} else if (tag.equals("license")) {
					IFeatureInfo info = getModel().getFactory().createInfo(IFeature.INFO_LICENSE);
					((FeatureInfo) info).parse(child);
					infos[IFeature.INFO_LICENSE] = info;
				} else if (tag.equals("copyright")) {
					IFeatureInfo info = getModel().getFactory().createInfo(IFeature.INFO_COPYRIGHT);
					((FeatureInfo) info).parse(child);
					infos[IFeature.INFO_COPYRIGHT] = info;
				} else if (tag.equals("url")) {
					if (url == null) {
						url = getModel().getFactory().createURL();
						((FeatureURL) url).parse(child);
					}
				} else if (tag.equals("requires")) {
					parseRequires(child);
				} else if (tag.equals("install-handler")) {
					IFeatureInstallHandler handler = getModel().getFactory().createInstallHandler();
					((FeatureInstallHandler) handler).parse(child);
					this.handler = handler;
				} else if (tag.equals("plugin")) {
					IFeaturePlugin plugin = getModel().getFactory().createPlugin();
					((FeaturePlugin) plugin).parse(child);
					plugins.add(plugin);
				} else if (tag.equals("data")) {
					IFeatureData newData = getModel().getFactory().createData();
					((FeatureData) newData).parse(child);
					data.add(newData);
				}
			}
		}
	}
	private void parseRequires(Node node) {
		NodeList children = node.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				if (child.getNodeName().equalsIgnoreCase("import")) {
					IFeatureImport iimport = getModel().getFactory().createImport();
					((FeatureImport) iimport).parse(child);
					imports.add(iimport);
				}
			}
		}
	}

	public void computeImports() throws CoreException {
		IFeatureImport[] removed = null;
		if (imports.size() > 0)
			removed =
				(IFeatureImport[]) imports.toArray(new IFeatureImport[imports.size()]);
		imports.clear();
		if (removed != null)
			fireStructureChanged(removed, IModelChangedEvent.REMOVE);
		// Create full import list
		for (int i = 0; i < plugins.size(); i++) {
			IFeaturePlugin fp = (IFeaturePlugin) plugins.get(i);
			IPluginBase plugin =
				PDEPlugin.getDefault().findPlugin(fp.getId(), fp.getVersion(), 0);
			if (plugin != null) {
				addPluginImports(plugin);
			}
		}
		// Find plug-ins that satisfy requirements within this feature.
		// Whatever remains will be feature external requirements.
		Vector inputImports = (Vector) imports.clone();
		for (int i = 0; i < inputImports.size(); i++) {
			IFeatureImport iimport = (IFeatureImport) inputImports.get(i);
			IFeaturePlugin local =
				findFeaturePlugin(iimport.getId(), iimport.getVersion(), iimport.getMatch());
			if (local != null)
				imports.remove(iimport);
		}
		if (imports.size() > 0) {
			IFeatureImport[] added =
				(IFeatureImport[]) imports.toArray(new IFeatureImport[imports.size()]);
			fireStructureChanged(added, IModelChangedEvent.INSERT);
		}
	}

	private void addPluginImports(IPluginBase plugin) throws CoreException {
		IPluginImport[] pluginImports = plugin.getImports();
		for (int i = 0; i < pluginImports.length; i++) {
			IPluginImport pluginImport = pluginImports[i];
			String id = pluginImport.getId();
			String version = pluginImport.getVersion();
			int match = pluginImport.getMatch();
			// Don't add duplicates
			if (findImport(id, version, match) != null)
				continue;
			IFeatureImport iimport = getModel().getFactory().createImport();
			iimport.setId(id);
			iimport.setVersion(version);
			iimport.setMatch(match);
			imports.add(iimport);
			IPlugin p =
				PDEPlugin.getDefault().findPlugin(
					pluginImport.getId(),
					pluginImport.getVersion(),
					pluginImport.getMatch());
			((FeatureImport) iimport).setPlugin(p);
			/*
			if (p != null)
				addPluginImports(p);
			*/
		}
	}

	private IFeatureImport findImport(String id, String version, int match) {
		for (int i = 0; i < imports.size(); i++) {
			IFeatureImport iimport = (IFeatureImport) imports.get(i);
			if (iimport.getId().equals(id)) {
				if (version == null)
					return iimport;
				if (version.equals(iimport.getVersion()) && match == iimport.getMatch())
					return iimport;
			}
		}
		return null;
	}

	private IFeaturePlugin findFeaturePlugin(
		String id,
		String version,
		int match) {

		for (int i = 0; i < plugins.size(); i++) {
			IFeaturePlugin fp = (IFeaturePlugin) plugins.get(i);
			String pid = fp.getId();
			String pversion = fp.getVersion();
			if (PDEPlugin.compare(id, version, pid, pversion, match))
				return fp;
		}
		return null;
	}

	public void removePlugins(IFeaturePlugin[] removed) throws CoreException {
		ensureModelEditable();
		for (int i = 0; i < removed.length; i++)
			plugins.remove(removed[i]);
		fireStructureChanged(removed, IModelChangedEvent.REMOVE);
	}

	public void removeData(IFeatureData[] removed) throws CoreException {
		ensureModelEditable();
		for (int i = 0; i < removed.length; i++)
			data.remove(removed[i]);
		fireStructureChanged(removed, IModelChangedEvent.REMOVE);
	}
	public void removeImport(IFeatureImport iimport) throws CoreException {
		ensureModelEditable();
		imports.remove(iimport);
		fireStructureChanged(iimport, IModelChangedEvent.REMOVE);
	}

	public String getOS() {
		return os;
	}

	public String getWS() {
		return ws;
	}

	public String getNL() {
		return nl;
	}
	
	
	public String getArch() {
		return arch;
	}

	public void setOS(String os) throws CoreException {
		ensureModelEditable();
		Object oldValue = this.os;
		this.os = os;
		firePropertyChanged(P_OS, oldValue, os);
	}
	public void setWS(String ws) throws CoreException {
		ensureModelEditable();
		Object oldValue = this.ws;
		this.ws = ws;
		firePropertyChanged(P_WS, oldValue, ws);
	}
	public void setNL(String nl) throws CoreException {
		ensureModelEditable();
		Object oldValue = this.nl;
		this.nl = nl;
		firePropertyChanged(P_NL, oldValue, nl);
	}
	public void setArch(String arch) throws CoreException {
		ensureModelEditable();
		Object oldValue = this.arch;
		this.arch = arch;
		firePropertyChanged(P_ARCH, oldValue, arch);
	}

	public void reset() {
		super.reset();
		data.clear();
		plugins.clear();
		imports.clear();
		url = null;
		providerName = null;
		os = null;
		ws = null;
		nl = null;
		arch = null;
	}

	public void setProviderName(String providerName) throws CoreException {
		ensureModelEditable();
		Object oldValue = this.providerName;
		this.providerName = providerName;
		firePropertyChanged(P_PROVIDER, oldValue, providerName);
	}
	public void setURL(IFeatureURL url) throws CoreException {
		ensureModelEditable();
		Object oldValue = this.url;
		this.url = url;
		firePropertyChanged(P_URL, oldValue, url);
	}
	public void setInstallHandler(IFeatureInstallHandler handler)
		throws CoreException {
		ensureModelEditable();
		Object oldValue = this.handler;
		this.handler = handler;
		firePropertyChanged(P_INSTALL_HANDLER, oldValue, handler);
	}

	public IFeatureInfo getFeatureInfo(int index) {
		return infos[index];
	}

	public void setFeatureInfo(IFeatureInfo info, int index) throws CoreException {
		ensureModelEditable();
		Object oldValue = infos[index];
		infos[index] = info;
		String property;
		switch (index) {
			case INFO_DESCRIPTION :
				property = P_DESCRIPTION;
				break;
			case INFO_LICENSE :
				property = P_LICENSE;
				break;
			case INFO_COPYRIGHT :
				property = P_COPYRIGHT;
				break;
			default :
				return;
		}
		firePropertyChanged(property, oldValue, info);
	}

	public void write(String indent, PrintWriter writer) {
		writer.print(indent + "<feature");
		String indent2 = indent + INDENT;
		String indenta = indent + INDENT + INDENT;
		writeIfDefined(indenta, writer, "id", getId());
		writeIfDefined(indenta, writer, "label", getWritableString(getLabel()));
		writeIfDefined(indenta, writer, "version", getVersion());
		writeIfDefined(
			indenta,
			writer,
			"provider-name",
			getWritableString(providerName));
		writeIfDefined(indenta, writer, "os", os);
		writeIfDefined(indenta, writer, "ws", ws);
		writeIfDefined(indenta, writer, "nl", nl);
		writeIfDefined(indenta, writer, "arch", arch);
		if (isPrimary()) {
			writer.println();
			writer.print(indenta + "primary=\"true\"");
		}

		writer.println(">");
		if (handler != null) {
			writer.println();
			handler.write(indent2, writer);
		}

		for (int i = 0; i < 3; i++) {
			IFeatureInfo info = infos[i];
			if (info != null && !info.isEmpty())
				info.write(indent2, writer);
		}

		if (url != null) {
			writer.println();
			url.write(indent2, writer);
		}
		if (imports.size() > 0) {
			writer.println();
			writer.println(indent2 + "<requires>");
			for (int i = 0; i < imports.size(); i++) {
				IFeatureImport iimport = (IFeatureImport) imports.get(i);
				iimport.write(indenta, writer);
			}
			writer.println(indent2 + "</requires>");
		}
		for (int i = 0; i < plugins.size(); i++) {
			IFeaturePlugin plugin = (IFeaturePlugin) plugins.elementAt(i);
			writer.println();
			plugin.write(indent2, writer);
		}
		for (int i = 0; i < data.size(); i++) {
			IFeatureData entry = (IFeatureData) data.elementAt(i);
			writer.println();
			entry.write(indent2, writer);
		}
		writer.println();
		writer.println(indent + "</feature>");
	}
	private void writeIfDefined(
		String indent,
		PrintWriter writer,
		String attName,
		String attValue) {
		if (attValue == null)
			return;
		writer.println();
		writer.print(indent + attName + "=\"" + attValue + "\"");
	}
}