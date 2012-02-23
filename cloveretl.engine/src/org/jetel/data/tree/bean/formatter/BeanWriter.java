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
package org.jetel.data.tree.bean.formatter;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.jetel.data.DataField;
import org.jetel.data.tree.bean.BeanConstants;
import org.jetel.data.tree.bean.SimpleTypes;
import org.jetel.data.tree.bean.schema.model.SchemaCollection;
import org.jetel.data.tree.bean.schema.model.SchemaMap;
import org.jetel.data.tree.bean.schema.model.SchemaObject;
import org.jetel.data.tree.bean.schema.model.TypedObject;
import org.jetel.data.tree.bean.schema.model.TypedObjectRef;
import org.jetel.data.tree.formatter.CollectionWriter;
import org.jetel.data.tree.formatter.NamespaceWriter;
import org.jetel.data.tree.formatter.TreeWriter;
import org.jetel.exception.JetelException;

/**
 * @author lkrejci (info@cloveretl.com) (c) Javlin, a.s. (www.cloveretl.com)
 * 
 * @created 4.11.2011
 */
public class BeanWriter implements TreeWriter, CollectionWriter, NamespaceWriter {

	private static enum State {
		TREE, BEAN, PROPERTY, COLLECTION, MAP, MAP_ENTRY
	}

	@SuppressWarnings("rawtypes")
	private static final Class<? extends Map> DEFAULT_MAP_CLASS = LinkedHashMap.class;
	@SuppressWarnings("rawtypes")
	private static final Class<? extends Collection> DEFAULT_COLLECTION_CLASS = ArrayList.class;

	private ClassLoader classloader;

	private SchemaObject structure;
	private SchemaObject currentStructure;
	private Stack<TypedObjectRef> typedObjectParent = new Stack<TypedObjectRef>();
	private Stack<BeanWriter.State> stateStack = new Stack<BeanWriter.State>();

	private Stack<Object> beanStack = new Stack<Object>();
	private String propertyToFill;
	private Class<?> propertyClass;

	private Object result;

	public BeanWriter(SchemaObject structure, ClassLoader classLoader) {
		this.structure = structure;
		this.classloader = classLoader;
	}

	@Override
	public void writeStartTree() throws JetelException {
		stateStack.push(State.TREE);
	}

	@Override
	public void writeStartNode(char[] name) throws JetelException {
		switch (stateStack.peek()) {
		case TREE:
			handleTreeStartNode();
			return;
		case BEAN:
			handleBeanStart(new String(name));
			return;
		case COLLECTION:
			handleCollectionStart();
			return;
		case MAP:
			handleMapStart();
			return;
		case MAP_ENTRY:
			handleMapEntryStart(new String(name));
			return;
		}
	}

	@Override
	public void writeStartCollection(char[] collectionName) throws JetelException {
		// Collection behaves pretty much the same as node since types are stored structure Object not mapping itself.
		// however during compilation of mapping children of collection node with binding are wrapped with synthetic
		// object node
		// so that start and end of collection item is clearly marked.
		writeStartNode(collectionName);
	}

	private void handleTreeStartNode() throws JetelException {
		pushCurrentStructure(structure);
		String beanClass;
		if (structure instanceof TypedObjectRef) {
			beanClass = ((TypedObjectRef) structure).getTypedObject().getType();
		} else {
			beanClass = structure.getType();
		}

		Class<?> proposedClass = null;
		if (beanClass != null) {
			try {
				proposedClass = classloader.loadClass(beanClass);
			} catch (ClassNotFoundException e) {
				throw new JetelException(e.getMessage(), e);
			}
		}
		beanStack.push(createBeanForState(currentStructure, proposedClass));
		stateStack.push(createState(currentStructure));

		return;
	}

	private void handleBeanStart(String name) throws JetelException {
		try {
			TypedObject typedObject = (TypedObject) currentStructure;
			pushCurrentStructure(typedObject.getChild(name));

			Class<?> nestedClass = SimpleTypes.getPrimitiveClass(currentStructure.getType());
			if (nestedClass == null) {
				nestedClass = Class.forName(currentStructure.getType(), true, classloader);
			}

			if (SimpleTypes.isSimpleType(nestedClass)) {
				propertyToFill = name;
				propertyClass = nestedClass;
				stateStack.push(State.PROPERTY);
			} else {
				initBean(name, nestedClass);
			}
		} catch (ClassNotFoundException e) {
			throw new JetelException(e.getMessage(), e);
		}
	}

	private void initBean(String name, Class<?> nestedClass) throws JetelException {
		try {
			Object currentBean = beanStack.peek();
			Object nestedBean = PropertyUtils.getProperty(currentBean, name);
			if (nestedBean == null) {
				nestedBean = createBeanForState(currentStructure, nestedClass);
			}

			stateStack.push(createState(currentStructure));
			beanStack.push(nestedBean);
		} catch (IllegalAccessException e) {
			throw new JetelException(e.getMessage(), e);
		} catch (InvocationTargetException e) {
			throw new JetelException(e.getMessage(), e);
		} catch (NoSuchMethodException e) {
			throw new JetelException(e.getMessage(), e);
		}
	}

	private void handleCollectionStart() throws JetelException {
		try {
			SchemaCollection collection = (SchemaCollection) currentStructure;
			pushCurrentStructure(collection.getItem());

			Class<?> nestedClass = SimpleTypes.getPrimitiveClass(currentStructure.getType());
			if (nestedClass == null) {
				nestedClass = Class.forName(currentStructure.getType(), true, classloader);
			}
			beanStack.push(new CollectionEntry());
			if (SimpleTypes.isSimpleType(nestedClass)) {
				propertyToFill = BeanConstants.LIST_ITEM_ELEMENT_NAME;
				propertyClass = nestedClass;
				stateStack.push(State.PROPERTY);
			} else {
				initBean(BeanConstants.LIST_ITEM_ELEMENT_NAME, nestedClass);
			}
		} catch (ClassNotFoundException e) {
			throw new JetelException(e.getMessage(), e);
		}
	}

	private void handleMapStart() {
		beanStack.push(new MapEntry());
		stateStack.push(State.MAP_ENTRY);
	}

	private void handleMapEntryStart(String name) throws JetelException {
		try {
			SchemaMap map = (SchemaMap) currentStructure;
			if (BeanConstants.MAP_KEY_ELEMENT_NAME.equals(name)) {
				pushCurrentStructure(map.getKey());
			} else { // This must be value - should be checked by validator
				pushCurrentStructure(map.getValue());
			}

			Class<?> nestedClass = classloader.loadClass(currentStructure.getType());
			if (SimpleTypes.isSimpleType(nestedClass)) {
				propertyClass = nestedClass;
				stateStack.push(State.PROPERTY);
				if (BeanConstants.MAP_KEY_ELEMENT_NAME.equals(name)) {
					propertyToFill = BeanConstants.MAP_KEY_ELEMENT_NAME;
				} else { // This must be value
					propertyToFill = BeanConstants.MAP_VALUE_ELEMENT_NAME;
				}
			} else {
				initBean(name, nestedClass);
			}
		} catch (ClassNotFoundException e) {
			throw new JetelException(e.getMessage(), e);
		}
	}

	private void pushCurrentStructure(SchemaObject structure) {
		if (structure instanceof TypedObjectRef) {
			TypedObjectRef nestedTypedObjectRef = (TypedObjectRef) structure;
			typedObjectParent.push(nestedTypedObjectRef);

			currentStructure = nestedTypedObjectRef.getTypedObject();
		} else {
			currentStructure = structure;
		}
	}

	private void popCurrentStructure() {
		if (currentStructure.getParent() == null) {
			if (typedObjectParent.isEmpty()) {
				currentStructure = null;
				return;
			}
			currentStructure = typedObjectParent.pop();
		}

		currentStructure = currentStructure.getParent();
	}

	private State createState(SchemaObject structure) {
		if (structure instanceof SchemaMap) {
			return State.MAP;
		} else if (structure instanceof SchemaCollection) {
			return State.COLLECTION;
		} else {
			return State.BEAN;
		}
	}

	private Object createBeanForState(SchemaObject structure, Class<?> proposedClass) throws JetelException {
		try {
			Object toReturn;
			if (structure instanceof TypedObjectRef || structure instanceof TypedObject) {
				toReturn = proposedClass.newInstance();
			} else if (structure instanceof SchemaCollection) {
				if (proposedClass == null || proposedClass.isInterface()) {
					toReturn = DEFAULT_COLLECTION_CLASS.newInstance();
				} else {
					if (proposedClass.isArray()) {
						toReturn = Array.newInstance(proposedClass.getComponentType(), 0);
					} else {
						toReturn = proposedClass.newInstance();
					}
				}
			} else if (structure instanceof SchemaMap) {
				if (proposedClass == null || proposedClass.isInterface()) {
					toReturn = DEFAULT_MAP_CLASS.newInstance();
				} else {
					toReturn = proposedClass.newInstance();
				}
			} else {
				throw new IllegalArgumentException("Unknown type of structure");
			}

			return toReturn;
		} catch (InstantiationException e) {
			throw new JetelException("Unable to create bean", e);
		} catch (IllegalAccessException e) {
			throw new JetelException("Unable to create bean", e);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void writeLeaf(Object content) throws JetelException {
		Object actualContent;
		if (content instanceof DataField) {
			actualContent = ((DataField) content).getValue();
		} else if (content instanceof String) {
			actualContent = (String) content;
		} else {
			throw new IllegalArgumentException("Unknown type of property content");
		}

		if (actualContent instanceof List<?>) {
			List<?> listContent = (List<?>) actualContent;
			for (Object object : listContent) { // FIXME: optimize me!
				pushItemToCollection(object);
			}
		} else {
			try {

				if (propertyClass.isEnum() && actualContent instanceof CharSequence) {
					Class<Enum> enumType = (Class<Enum>) propertyClass;
					String enumString = ((CharSequence) actualContent).toString();
					try {
						actualContent = Enum.valueOf(enumType, enumString);
					} catch (Exception e) {
						throw new JetelException("Unable to fill property " + propertyToFill, e);
					}
				} else {
					actualContent = ConvertUtils.convert(actualContent, propertyClass);
				}
				PropertyUtils.setProperty(beanStack.peek(), propertyToFill, actualContent);
			} catch (IllegalAccessException e) {
				throw new JetelException("Unable to fill property " + propertyToFill, e);
			} catch (InvocationTargetException e) {
				throw new JetelException("Unable to fill property " + propertyToFill, e);
			} catch (NoSuchMethodException e) {
				throw new JetelException("Unable to fill property " + propertyToFill, e);
			}
		}

	}

	@Override
	public void writeEndNode(char[] name) throws JetelException {
		String actualName = new String(name);
		switch (stateStack.pop()) {
		case BEAN:
		case COLLECTION:
		case MAP:
			result = beanStack.pop();
			if (stateStack.peek() != State.TREE) {
				pushPropertyToBean(result, actualName);
				pushCollectionEntry();
				popCurrentStructure();
			}
			break;

		case MAP_ENTRY:
			pushCollectionEntry();
			pushMapEntry();
			break;

		case PROPERTY:
			propertyToFill = null;
			propertyClass = null;
			pushCollectionEntry();
			popCurrentStructure();
			break;
		}
	}

	@Override
	public void writeEndCollection(char[] collectionName) throws JetelException {
		writeEndNode(collectionName);
	}

	private void pushPropertyToBean(Object value, String propertyName) throws JetelException {
		if (beanStack.size() >= 1) {
			Object bean = beanStack.peek();
			try {
				if (bean instanceof CollectionEntry) {
					PropertyUtils.setProperty(bean, BeanConstants.LIST_ITEM_ELEMENT_NAME, value);
				} else {
					PropertyUtils.setProperty(bean, propertyName, value);
				}
			} catch (IllegalAccessException e) {
				throw new JetelException("Unable to fill property " + currentStructure.getName(), e);
			} catch (InvocationTargetException e) {
				throw new JetelException("Unable to fill property " + currentStructure.getName(), e);
			} catch (NoSuchMethodException e) {
				throw new JetelException("Unable to fill property " + currentStructure.getName(), e);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void pushMapEntry() {
		MapEntry mapEntry = (MapEntry) beanStack.pop();
		Map<Object, Object> typedMap = (Map<Object, Object>) beanStack.peek();
		typedMap.put(mapEntry.getKey(), mapEntry.getValue());
	}

	private void pushCollectionEntry() throws JetelException {
		if (stateStack.peek() != State.COLLECTION) {
			return;
		}

		CollectionEntry entry = (CollectionEntry) beanStack.pop();
		pushItemToCollection(entry.getItem());
	}

	@SuppressWarnings("unchecked")
	private void pushItemToCollection(Object item) {
		Object collection = beanStack.peek();
		if (collection.getClass().isArray()) {
			beanStack.pop(); // remove it from the stack as it is going to be replaced

			int currentLength = Array.getLength(collection);
			Object newArray = Array.newInstance(collection.getClass().getComponentType(), currentLength + 1);
			System.arraycopy(collection, 0, newArray, 0, currentLength);
			Array.set(newArray, currentLength, item);
			beanStack.push(newArray);
		} else {
			Collection<Object> typedCollection = (Collection<Object>) collection;
			typedCollection.add(item);
		}
	}

	@Override
	public void writeEndTree() throws JetelException {
		stateStack.pop();
	}

	public Object flushBean() {
		Object toReturn = result;
		result = null;
		return toReturn;
	}

	public static class MapEntry {

		// property name must match BeanConstants.MAP_KEY_ELEMENT_NAME
		private Object key;
		// property name must match BeanConstants.MAP_VALUE_ELEMENT_NAME
		private Object value;

		public Object getKey() {
			return key;
		}

		public void setKey(Object key) {
			this.key = key;
		}

		public Object getValue() {
			return value;
		}

		public void setValue(Object value) {
			this.value = value;
		}
	}

	public static class CollectionEntry {

		// Property name must match BeanConstants.LIST_ELEMENT_NAME
		private Object item;

		public Object getItem() {
			return item;
		}

		public void setItem(Object item) {
			this.item = item;
		}
	}

	/**
	 * BeanWriter implements NamespaceWriter just because mapping is being validated to generated schema from bean
	 * structure. Namespaces do not make any sense in bean writing
	 */
	@Override
	public void writeNamespace(char[] prefix, char[] namespaceURI) throws JetelException {
		// Do nothing
	}
}
