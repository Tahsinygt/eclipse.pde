/*******************************************************************************
 * Copyright (c) 2007, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.api.tools.internal.model;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.pde.api.tools.internal.provisional.model.ApiTypeContainerVisitor;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiElement;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiTypeContainer;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiTypeRoot;
import org.eclipse.pde.api.tools.internal.util.Util;

/**
 * An {@link IApiTypeContainer} rooted at a directory in the file system.
 * 
 * @since 1.0.0
 */
public class DirectoryApiTypeContainer extends ApiElement implements IApiTypeContainer {
	
	/**
	 * Implementation of an {@link IApiTypeRoot} in the local file system.
	 */
	static class LocalApiTypeRoot extends AbstractApiTypeRoot implements Comparable {
		
		/**
		 * Associated file
		 */
		private File fFile;
		
		/**
		 * Constructs a class file on the given file
		 * @param directory the parent {@link IApiElement} directory
		 * @param file file
		 * @param qualified type name
		 * @param component owning API component
		 */
		public LocalApiTypeRoot(DirectoryApiTypeContainer directory, File file, String typeName) {
			super(directory, typeName);
			fFile = file;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.pde.api.tools.model.component.IClassFile#getTypeName()
		 */
		public String getTypeName() {
			return getName();
		}

		/* (non-Javadoc)
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		public int compareTo(Object o) {
			return getName().compareTo(((LocalApiTypeRoot)o).getName());
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		public boolean equals(Object obj) {
			if (obj instanceof LocalApiTypeRoot) {
				return ((LocalApiTypeRoot) obj).getName().equals(this.getName());
			}
			return false;
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		public int hashCode() {
			return this.getName().hashCode();
		}

		/* (non-Javadoc)
		 * @see org.eclipse.pde.api.tools.model.component.IClassFile#getInputStream()
		 */
		public InputStream getInputStream() throws CoreException {
			try {
				return new FileInputStream(fFile);
			} catch (FileNotFoundException e) {
				abort("File not found", e); //$NON-NLS-1$
			}
			return null; // never reaches here
		}
	}	
	
	/**
	 * Root directory of the class file container
	 */
	private File fRoot;

	/**
	 * Map of package names to associated directory (file)
	 */
	private Map fPackages;
	
	/**
	 * Cache of package names
	 */
	private String[] fPackageNames;
	
	/**
	 * Constructs an {@link IApiTypeContainer} rooted at the specified path.
	 * 
	 * @param parent the parent {@link IApiElement} or <code>null</code> if none
	 * @param location absolute path in the local file system
	 */
	public DirectoryApiTypeContainer(IApiElement parent, String location) {
		super(parent, IApiElement.API_TYPE_CONTAINER, location);
		this.fRoot = new File(location);
	}
	
	/**
	 * @see org.eclipse.pde.api.tools.internal.provisional.IApiTypeContainer#accept(org.eclipse.pde.api.tools.internal.provisional.ApiTypeContainerVisitor)
	 */
	public void accept(ApiTypeContainerVisitor visitor) throws CoreException {
		init();
		String[] packageNames = getPackageNames();
		for (int i = 0; i < packageNames.length; i++) {
			String pkg = packageNames[i];
			if (visitor.visitPackage(pkg)) {
				File dir = (File) fPackages.get(pkg);
				File[] files = dir.listFiles(new FileFilter() {
					public boolean accept(File file) {
						return file.isFile() && file.getName().endsWith(Util.DOT_CLASS_SUFFIX);
					}
				});
				if (files != null) {
					List classFiles = new ArrayList();
					for (int j = 0; j < files.length; j++) {
						File file = files[j];
						String name = file.getName();
						String typeName = name.substring(0, name.length() - 6);
						if (pkg.length() > 0) {
							typeName = pkg + "." + typeName; //$NON-NLS-1$
						}
						classFiles.add(new LocalApiTypeRoot(this, file, typeName));
					}
					Collections.sort(classFiles);
					Iterator cfIterator = classFiles.iterator();
					while (cfIterator.hasNext()) {
						IApiTypeRoot classFile = (IApiTypeRoot) cfIterator.next();
						visitor.visit(pkg, classFile);
						visitor.end(pkg, classFile);
					}
				}
			}
			visitor.endVisitPackage(pkg);
		}
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		StringBuffer buff = new StringBuffer();
		buff.append("Directory Class File Container: "+getName()); //$NON-NLS-1$
		return buff.toString();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.model.component.IClassFileContainer#close()
	 */
	public synchronized void close() throws CoreException {
		fPackages = null;
		fPackageNames = null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.model.component.IClassFileContainer#findClassFile(java.lang.String)
	 */
	public IApiTypeRoot findTypeRoot(String qualifiedName) throws CoreException {
		init();
		int index = qualifiedName.lastIndexOf('.');
		String cfName = qualifiedName;
		String pkg = Util.DEFAULT_PACKAGE_NAME;
		if (index > 0) {
			pkg = qualifiedName.substring(0, index);
			cfName = qualifiedName.substring(index + 1);
		}
		File dir = (File) fPackages.get(pkg);
		if (dir != null) {
			File file = new File(dir, cfName + Util.DOT_CLASS_SUFFIX);
			if (file.exists()) {
				return new LocalApiTypeRoot(this, file, qualifiedName);
			}
		}
		return null;
	}

	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.api.tools.model.component.IClassFileContainer#getPackageNames()
	 */
	public String[] getPackageNames() throws CoreException {
		init();
		if (fPackageNames == null) {
			List names = new ArrayList(fPackages.keySet());
			String[] result = new String[names.size()];
			names.toArray(result);
			Arrays.sort(result);
			fPackageNames = result;
		}
		return fPackageNames;
	}
	
	/**
	 * Builds cache of package names to directories
	 */
	private synchronized void init() {
		if (fPackages == null) {
			fPackages = new HashMap();
			processDirectory(Util.DEFAULT_PACKAGE_NAME, fRoot);
		}
	}
	
	/**
	 * Traverses a directory to determine if it has class files and
	 * then visits sub-directories.
	 * 
	 * @param packageName package name of directory being visited
	 * @param dir directory being visited
	 */
	private void processDirectory(String packageName, File dir) {
		File[] files = dir.listFiles();
		if (files != null) {
			boolean hasClassFiles = false;
			List dirs = new ArrayList();
			for (int i = 0; i < files.length; i++) {
				File file = files[i];
				if (file.isDirectory()) {
					dirs.add(file);
				} else if (!hasClassFiles) {
					if (file.getName().endsWith(Util.DOT_CLASS_SUFFIX)) {
						fPackages.put(packageName, dir);
						hasClassFiles = true;
					}
				}
			}
			Iterator iterator = dirs.iterator();
			while (iterator.hasNext()) {
				File child = (File)iterator.next();
				String nextName = null;
				if (packageName.length() == 0) {
					nextName = child.getName();
				} else {
					nextName = packageName + "." + child.getName(); //$NON-NLS-1$
				}
				processDirectory(nextName, child);
			}
		}
	}

	/**
	 * @see org.eclipse.pde.api.tools.internal.provisional.IApiTypeContainer#findClassFile(java.lang.String, java.lang.String)
	 */
	public IApiTypeRoot findTypeRoot(String qualifiedName, String id) throws CoreException {
		return findTypeRoot(qualifiedName);
	}
}
