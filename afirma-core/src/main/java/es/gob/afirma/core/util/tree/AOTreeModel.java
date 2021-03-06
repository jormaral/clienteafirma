/* Copyright (C) 2011 [Gobierno de Espana]
 * This file is part of "Cliente @Firma".
 * "Cliente @Firma" is free software; you can redistribute it and/or modify it under the terms of:
 *   - the GNU General Public License as published by the Free Software Foundation; 
 *     either version 2 of the License, or (at your option) any later version.
 *   - or The European Software License; either version 1.1 or (at your option) any later version.
 * Date: 11/01/11
 * You may contact the copyright holder at: soporte.afirma5@mpt.es
 */

package es.gob.afirma.core.util.tree;

/** Adaptaci&oacute;n de las clases AOTreeModel de Swing para su uso sin interfaz gr6aacute;fico. */
public final class AOTreeModel {

    /** Root of the tree. */
    private final AOTreeNode root;

    /** Children count. Always 1 starting with the root */
    private int count = 1;

    /** Obtiene el n&uacute;mero de elementos del &aacute;rbol.
     * @return N&uacute;mero de elementos del &aacute;rbol */
    public Integer getCount() {
        return Integer.valueOf(this.count);
    }

    /** Determines how the <code>isLeaf</code> method figures out if a node is a
     * leaf node. If true, a node is a leaf node if it does not allow children.
     * (If it allows children, it is not a leaf node, even if no children are
     * present.) That lets you distinguish between <i>folder</i> nodes and
     * <i>file</i> nodes in a file system, for example.
     * <p>
     * If this value is false, then any node which has no children is a leaf node, and any node may acquire children */
    private final boolean asksAllowsChildren;

    /** Crea un &aacute;rbol en el que cualquier nodo puede tener hijos.
     * @param root La ra&iacute;z del &aacute;rbol */
    public AOTreeModel(final AOTreeNode root) {
        this(root, false);
    }

    /** Creates a tree specifying whether any node can have children, or whether
     * only certain nodes can have children.
     * @param root
     *        a TreeNode object that is the root of the tree
     * @param asksAllowsChildren
     *        a boolean, false if any node can have children, true if each
     *        node is asked to see if it can have children
     * @see #asksAllowsChildren */
    private AOTreeModel(final AOTreeNode root, final boolean asksAllowsChildren) {
        super();
        this.root = root;
        this.asksAllowsChildren = asksAllowsChildren;
    }

    /** Construye un nuevo &aacute;rbol.
     * @param treeRoot Ra&iacute;z del &aacute;rbol
     * @param count N&uacute;mero de elementos iniciales del nuevo &aacute;rbol */
    public AOTreeModel(final AOTreeNode treeRoot, final int count) {
        this(treeRoot, false);
        this.count = count;
    }

    /** Tells how leaf nodes are determined.
     * @return true if only nodes which do not allow children are leaf nodes,
     *         false if nodes which have no children (even if allowed) are leaf
     *         nodes */
    public boolean asksAllowsChildren() {
        return this.asksAllowsChildren;
    }

    /** Returns the root of the tree. Returns null only if the tree has no nodes.
     * @return the root of the tree */
    public Object getRoot() {
        return this.root;
    }

    /** Returns the child of <I>parent</I> at index <I>index</I> in the parent's
     * child array. <I>parent</I> must be a node previously obtained from this
     * data source. This should not return null if <i>index</i> is a valid index
     * for <i>parent</i> (that is <i>index</i> >= 0 && <i>index</i> <
     * getChildCount(<i>parent</i>)).
     * @param index
     *        the position index of the child
     * @param parent
     *        a node in the tree, obtained from this data source
     * @return the child of <I>parent</I> at index <I>index</I> */
    public static Object getChild(final Object parent, final int index) {
        return ((AOTreeNode) parent).getChildAt(index);
    }

    /** Returns the number of children of <I>parent</I>. Returns 0 if the node is
     * a leaf or if it has no children. <I>parent</I> must be a node previously
     * obtained from this data source.
     * @param parent
     *        a node in the tree, obtained from this data source
     * @return the number of children of the node <I>parent</I> */
    public static int getChildCount(final Object parent) {
        return ((AOTreeNode) parent).getChildCount();
    }

}
