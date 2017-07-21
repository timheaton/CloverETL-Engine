/*
 * jETeL/CloverETL - Java based ETL application framework.
 * Copyright (c) Javlin, a.s. (info@cloveretl.com)
 *  
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.jetel.util;

import org.jetel.graph.Edge;

/**
 * Utility class for sub-graph related code.
 * 
 * @author Kokon (info@cloveretl.com)
 *         (c) Javlin, a.s. (www.cloveretl.com)
 *
 * @created 12.4.2013
 */
public class SubGraphUtils {

	/** Type of SubGraphInput component. */
	public static final String SUB_GRAPH_INPUT_TYPE = "SUB_GRAPH_INPUT";
	/** Type of SubGraphOutput component. */
	public static final String SUB_GRAPH_OUTPUT_TYPE = "SUB_GRAPH_OUTPUT";

	/** Type of SubJobflowInput component. */
	public static final String SUB_JOBFLOW_INPUT_TYPE = "SUB_JOBFLOW_INPUT";
	/** Type of SubJobflowOutput component. */
	public static final String SUB_JOBFLOW_OUTPUT_TYPE = "SUB_JOBFLOW_OUTPUT";

	/** Type of SubGraph component. */
	public static final String SUB_GRAPH_TYPE = "SUB_GRAPH";

	/** Type of SubJobflow component. */
	public static final String SUB_JOBFLOW_TYPE = "SUB_JOBFLOW";

    /** the name of an XML attribute used to pass a URL specified the executed sub-graph */
    public static final String XML_JOB_URL_ATTRIBUTE = "jobURL";

	/**
	 * @return true if and only if the given component type is SubGraphInput or SubJobflowInput component.
	 */
	public static boolean isSubJobInputComponent(String componentType) {
		return SUB_GRAPH_INPUT_TYPE.equals(componentType) || SUB_JOBFLOW_INPUT_TYPE.equals(componentType);
	}

	/**
	 * @return true if and only if the given component type is SubGraphOutput or SubJobflowOutput component.
	 */
	public static boolean isSubJobOutputComponent(String componentType) {
		return SUB_GRAPH_OUTPUT_TYPE.equals(componentType) || SUB_JOBFLOW_OUTPUT_TYPE.equals(componentType);
	}

	/**
	 * @return true if and only if the given component type is {@link #isSubJobInputComponent(String)} or {@link #isSubJobOutputComponent(String)}
	 */
	public static boolean isSubJobInputOutputComponent(String componentType) {
		return isSubJobInputComponent(componentType) || isSubJobOutputComponent(componentType);
	}
	
	/**
	 * @return true if and only if the given component type is SubGraph od SubJobflow component.
	 */
	public static boolean isSubJobComponent(String componentType) {
		return SUB_GRAPH_TYPE.equals(componentType) || SUB_JOBFLOW_TYPE.equals(componentType);
	}

	/**
	 * Checks whether output edge of SubGraphInput component can share EdgeBase
	 * with corresponding edge in parent graph. In regular cases, it is possible and
	 * highly recommended due performance gain. But in case edge debugging is turned on,
	 * sharing is not possible.
	 * @param subGraphEdge an output edge from SubGraphInput component
	 * @param parentGraphEdge corresponding edge from parent graph
	 * @return true if and only if the edge base from parentEdge can be shared with localEdge
	 */
	public static boolean isSubGraphInputEdgeShared(Edge subGraphEdge, Edge parentGraphEdge) {
		return subGraphEdge.getGraph().getRuntimeContext().isSubJob() && !subGraphEdge.isDebugMode();
	}

	/**
	 * Checks whether input edge of SubGraphOutput component can share EdgeBase
	 * with corresponding edge in parent graph. In regular cases, it is possible and
	 * highly recommended due performance gain. But in case edge debugging is turned on,
	 * sharing is not possible.
	 * @param subGraphEdge an input edge from SubGraphOutput component
	 * @param parentEdge corresponding edge from parent graph
	 * @return true if and only if the edge base from parentEdge can be shared with localEdge
	 */
	public static boolean isSubGraphOutputEdgeShared(Edge subGraphEdge, Edge parentGraphEdge) {
		return subGraphEdge.getGraph().getRuntimeContext().isSubJob() && !parentGraphEdge.isDebugMode();
	}
	
}