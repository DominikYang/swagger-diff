package com.deepoove.swagger.diff.compare;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.deepoove.swagger.diff.model.ElProperty;

import io.swagger.models.Model;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;

/**
 * compare two model
 * @author Sayi
 * @version
 */
public class ModelDiff {

	private List<ElProperty> increased;
	private List<ElProperty> missing;
	private List<ElProperty> changed;

	Map<String, Model> oldDedinitions;
	Map<String, Model> newDedinitions;

	private ModelDiff() {
		increased = new ArrayList<ElProperty>();
		missing = new ArrayList<ElProperty>();
		changed = new ArrayList<ElProperty>();
	}

	public static ModelDiff buildWithDefinition(Map<String, Model> left,
			Map<String, Model> right) {
		ModelDiff diff = new ModelDiff();
		diff.oldDedinitions = left;
		diff.newDedinitions = right;
		return diff;
	}

	public ModelDiff diff(Model leftModel, Model rightModel) {
		return this.diff(leftModel, rightModel, null, new HashSet<Model>());
	}

	public ModelDiff diff(Model leftModel, Model rightModel, String parentEl) {
		return this.diff(leftModel, rightModel, parentEl, new HashSet<Model>());
	}

	private ModelDiff diff(Model leftModel, Model rightModel, String parentEl, Set<Model> visited) {
		// Stop recursing if both models are null
		// OR either model is already contained in the visiting history
		if ((null == leftModel && null == rightModel) || visited.contains(leftModel) || visited.contains(rightModel)) {
			return this;
		}
		Map<String, Property> leftProperties = null == leftModel ? null : leftModel.getProperties();
		Map<String, Property> rightProperties = null == rightModel ? null : rightModel.getProperties();

		// Diff the properties
		MapKeyDiff<String, Property> propertyDiff = MapKeyDiff.diff(leftProperties, rightProperties);

		increased.addAll(buildElProperties(propertyDiff.getIncreased(), parentEl, false, new HashSet<Model>()));
		missing.addAll(buildElProperties(propertyDiff.getMissing(), parentEl, true, new HashSet<Model>()));

		// Recursively find the diff between properties
		List<String> sharedKey = propertyDiff.getSharedKey();
		for (String key : sharedKey) {
			Property left = leftProperties.get(key);
			Property right = rightProperties.get(key);

			if (RefProperty.class.isInstance(left) && RefProperty.class.isInstance(right)) {
				String leftRef = ((RefProperty) left).getSimpleRef();
				String rightRef = ((RefProperty) right).getSimpleRef();

				diff(oldDedinitions.get(leftRef), newDedinitions.get(rightRef),
						null == parentEl ? key : (parentEl + "." + key),
						copyAndAdd(visited, leftModel, rightModel));

			} else if (left != null && right != null && !left.equals(right)) {
				// Add a changed ElProperty if not a Reference
				changed.add(buildElProperty(key, parentEl, left));
			}
		}
		return this;
	}

	private Collection<? extends ElProperty> buildElProperties(
			Map<String, Property> propMap, String parentEl, boolean isLeft, Set<Model> visited) {

		List<ElProperty> result = new ArrayList<ElProperty>();
		if (null == propMap) {
			return result;
		}

		for (Entry<String, Property> entry : propMap.entrySet()) {
			String propName = entry.getKey();
			Property property = entry.getValue();

			if (!RefProperty.class.isInstance(property)) {
				// Add an ElProperty for non-ref changes
				result.add(buildElProperty(propName, parentEl, property));
			} else {
				String ref = ((RefProperty) property).getSimpleRef();
				Model model = isLeft ? oldDedinitions.get(ref) : newDedinitions.get(ref);

				// Only recurse if this ref hasn't been visited
				// in the direct history
				if (model != null && !visited.contains(ref)) {
					Map<String, Property> properties = model.getProperties();
					result.addAll(buildElProperties(properties, buildElString(parentEl, propName), isLeft, copyAndAdd(visited, model)));
					return result;
				}
			}
		}
		return result;
	}

	private String buildElString(String parentEl, String propName) {
		return null == parentEl ? propName : (parentEl + "." + propName);
	}

	private ElProperty buildElProperty(String propName, String parentEl, Property property) {
		ElProperty pWithPath = new ElProperty();
		pWithPath.setProperty(property);
		pWithPath.setEl(null == parentEl ? propName
				: (parentEl + "." + propName));
		return pWithPath;
	}

	private <T> Set<T> copyAndAdd(Set<T> set, T... add) {
		Set<T> newSet = new HashSet<T>(set);
		newSet.addAll(Arrays.asList(add));
		return newSet;
	}

	public List<ElProperty> getIncreased() {
		return increased;
	}

	public void setIncreased(List<ElProperty> increased) {
		this.increased = increased;
	}

	public List<ElProperty> getMissing() {
		return missing;
	}

	public void setMissing(List<ElProperty> missing) {
		this.missing = missing;
	}

	public List<ElProperty> getChanged() {
		return changed;
	}

	public void setChanged(List<ElProperty> changed) {
		this.changed = changed;
	}
}
