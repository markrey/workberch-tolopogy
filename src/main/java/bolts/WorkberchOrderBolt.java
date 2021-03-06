package main.java.bolts;

import static main.java.utils.constants.WorkberchConstants.INDEX_FIELD;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import main.java.utils.WorkberchTuple;
import main.java.utils.cartesianindex.CartesianIndex;
import main.java.utils.cartesianindex.CartesianLeaf;
import main.java.utils.cartesianindex.CartesianNode;
import main.java.utils.redis.RedisException;
import main.java.utils.redis.RedisHandeler;
import backtype.storm.topology.BasicOutputCollector;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

abstract public class WorkberchOrderBolt extends WorkberchProvenanceBolt {
	
	//FIXME: This flag is temporally, we need to improve the good algorithm
	public static boolean REAL_ALGORITHM = false;

	private static final long serialVersionUID = 1L;

	private final Map<Long, WorkberchTuple> indexMap = new HashMap<Long, WorkberchTuple>();
	private final boolean ordered;
	private long lastIndex = 0L;

	private CartesianIndex createInitialIndexElement(final CartesianIndex indexTemplate) {
		if (indexTemplate.isLeafValue()) {
			return new CartesianLeaf(0L);
		} else {
			final List<CartesianIndex> initListNodes = new ArrayList<CartesianIndex>();
			for (final CartesianIndex initNodeTemplate : indexTemplate.getNodes()) {
				initListNodes.add(createInitialIndexElement(initNodeTemplate));
			}
			return new CartesianNode(initListNodes);
		}
	}

	private CartesianIndex createNextElementOnIndex(final CartesianIndex previewsIndex, final CartesianIndex templateIndex) {
		if (templateIndex.isLeafValue()) {
			final Long templateValue = templateIndex.getValue();
			if (templateValue > 0 && templateValue < previewsIndex.getValue()) {
				return new CartesianLeaf(0L);
			} else {
				return new CartesianLeaf(previewsIndex.getValue() + 1);
			}
		} else {
			final List<CartesianIndex> previewsNodes = Lists.reverse(previewsIndex.getNodes());
			final List<CartesianIndex> templateNodes = Lists.reverse(templateIndex.getNodes());
			final Iterator<CartesianIndex> iterPreviews = previewsNodes.iterator();
			final Iterator<CartesianIndex> iterTemplate = templateNodes.iterator();
			CartesianIndex nextSubPreviews;
			CartesianIndex nextSubTemplate;
			CartesianIndex nextSubValue;
			CartesianIndex resetTuple;

			final List<CartesianIndex> nextNewNodes = new ArrayList<CartesianIndex>();
			do {
				nextSubPreviews = iterPreviews.next();
				nextSubTemplate = iterTemplate.next();
				nextSubValue = createNextElementOnIndex(nextSubPreviews, nextSubTemplate);
				nextNewNodes.add(nextSubValue);
				resetTuple = createInitialIndexElement(nextSubTemplate);
			} while (resetTuple.equals(nextSubValue) && iterPreviews.hasNext());

			if (iterPreviews.hasNext()) {
				for (final Iterator<CartesianIndex> iterator = iterPreviews; iterator.hasNext();) {
					nextNewNodes.add(iterator.next());
				}
			}
			return new CartesianNode(Lists.reverse(nextNewNodes));
		}
	}

	private void makePlainIndex(final CartesianIndex templateIndex, final Map<CartesianIndex, WorkberchTuple> cartesianIndex) {
		if (REAL_ALGORITHM) {
			makePlainIndexOnMapRecurtion(templateIndex, cartesianIndex, new CartesianNode(new ArrayList<CartesianIndex>()));
		}
		else {
			makePlainIndexOnLinealIndex(templateIndex.getNodes(), cartesianIndex, new CartesianNode(new ArrayList<CartesianIndex>()));
		}		
	}
	
	private boolean makePlainIndexOnLinealIndex(final List<CartesianIndex> nodes, final Map<CartesianIndex, WorkberchTuple> cartesianIndex, final CartesianIndex currentKey) {
		final boolean isLastDimention = nodes.isEmpty();
		if (isLastDimention) {
			final boolean existValue;
			if(existValue = cartesianIndex.containsKey(currentKey)){
				indexMap.put(lastIndex, cartesianIndex.get(currentKey));
				lastIndex++;
				return existValue;
			}
			return existValue;
		} else {
			long indexValue = 0L;
			boolean existValues = false;
			boolean existSomeIndex = false;
			do {
				final CartesianIndex currentIndexLeaf = new CartesianLeaf(indexValue);
				currentKey.getNodes().add(currentIndexLeaf);
				existValues = makePlainIndexOnLinealIndex(nodes.subList(1, nodes.size()), cartesianIndex, currentKey);
				currentKey.getNodes().remove(currentIndexLeaf);
				indexValue++;
				existSomeIndex = existSomeIndex || existValues;
			} while (existValues);
			return existSomeIndex;
		}
	}

	private boolean makePlainIndexOnMapRecurtion(final CartesianIndex templateIndex, final Map<CartesianIndex, WorkberchTuple> cartesianIndex,
			final CartesianIndex currentKey) {
		final List<CartesianIndex> listOfNodes = templateIndex.getNodes();
		final CartesianIndex templateIndexHead = listOfNodes.iterator().next();
		final CartesianIndex templateIndexTail = new CartesianNode(listOfNodes.subList(1, listOfNodes.size()));
		final boolean isLastDimention = templateIndexTail.getNodes().isEmpty();

		CartesianIndex currentIndex = createInitialIndexElement(templateIndexHead);
		currentKey.getNodes().add(currentIndex);
		boolean someValueWork = false;

		boolean areMoreValuesAvailabe = true;
		while (areMoreValuesAvailabe) {
			final boolean tupleExists = isLastDimention ? cartesianIndex.containsKey(currentKey) : makePlainIndexOnMapRecurtion(templateIndexTail,
					cartesianIndex, currentKey);

			if (!tupleExists) {
				areMoreValuesAvailabe = updateTemplateWithLastTops(templateIndexHead, currentIndex);
			} else if (isLastDimention && tupleExists) {
				indexMap.put(lastIndex, cartesianIndex.get(currentKey));
				someValueWork = true;
				lastIndex++;
			}

			currentKey.getNodes().remove(currentKey.getNodes().size() - 1);
			currentIndex = createNextElementOnIndex(currentIndex, templateIndexHead);
			currentKey.getNodes().add(currentIndex);
		}
		currentKey.getNodes().remove(currentKey.getNodes().size() - 1);
		return someValueWork;
	}

	private boolean updateTemplateWithLastTops(final CartesianIndex templateIndexHead, final CartesianIndex currentKey) {
		if (templateIndexHead.isLeafValue()) {
			if (templateIndexHead.getValue() < 0L) {
				templateIndexHead.setValue(currentKey.getValue() - 1);
				return true;
			}
			return false;
		} else {
			final List<CartesianIndex> templateNodes = Lists.reverse(templateIndexHead.getNodes());
			final List<CartesianIndex> currentNodes = Lists.reverse(currentKey.getNodes());
			final Iterator<CartesianIndex> iterTemplate = templateNodes.iterator();
			final Iterator<CartesianIndex> iterCurrent = currentNodes.iterator();
			boolean areMoreValuesAvailabe;
			do {
				areMoreValuesAvailabe = updateTemplateWithLastTops(iterTemplate.next(), iterCurrent.next());
			} while (iterTemplate.hasNext() && !areMoreValuesAvailabe);
			return areMoreValuesAvailabe;
		}
	}

	private CartesianIndex cleanTemplateIndex(final CartesianIndex templateTemplate) {
		if (templateTemplate.isLeafValue()) {
			return new CartesianLeaf(-1L);
		} else {
			final List<CartesianIndex> initListNodes = new ArrayList<CartesianIndex>();
			for (final CartesianIndex initNodeTemplate : templateTemplate.getNodes()) {
				initListNodes.add(cleanTemplateIndex(initNodeTemplate));
			}
			return new CartesianNode(initListNodes);
		}
	}

	private void emitAllSavedTuplesInOrder(final CartesianIndex templateIndex, final BasicOutputCollector collector, final String uuid) {
		try {
			final Map<CartesianIndex, WorkberchTuple> cartesianIndex = RedisHandeler.loadCartesianIndexObjects(getBoltId());

			if (!templateIndex.isLeafValue()) {
				makePlainIndex(cleanTemplateIndex(templateIndex), cartesianIndex);
			}

			WorkberchTuple tuple;
			lastIndex = 0L;
			do {
				tuple = indexMap.get(lastIndex);
				tuple.setPlainIndex(lastIndex);
				executeOrdered(tuple, collector, !indexMap.containsKey(++lastIndex), uuid);
			} while (indexMap.containsKey(lastIndex));
		} catch (final RedisException e) {
			Throwables.propagate(e);
		}
	}

	private void processReceivedTupleCommingInOrder(final WorkberchTuple input, final BasicOutputCollector collector, final boolean lastValues,
			final String uuid) {
		final Long currentLong = (Long) input.getValues().get(INDEX_FIELD);
		final long currentIndex = currentLong.longValue();

		if (currentIndex > lastIndex) {
			indexMap.put(currentLong, input);
		} else if (currentIndex == lastIndex) {
			indexMap.put(currentLong, input);
			WorkberchTuple tuple;
			do {
				tuple = indexMap.get(lastIndex);
				executeOrdered(tuple, collector, !indexMap.containsKey(++lastIndex) && lastValues, uuid);
			} while (indexMap.containsKey(lastIndex));
		}
	}

	private void proccessReceivedTupleCommingWithoutOrder(final WorkberchTuple input) {
		try {
			RedisHandeler.saveCartesianIndexObject(getBoltId(), input);
		} catch (final RedisException e) {
			Throwables.propagate(e);
		}
	}

	private void processReceivedTuple(final WorkberchTuple input, final BasicOutputCollector collector, final boolean lastValues, final String uuid) {
		if (ordered) {
			processReceivedTupleCommingInOrder(input, collector, lastValues, uuid);
		} else {
			proccessReceivedTupleCommingWithoutOrder(input);
		}
	};

	public WorkberchOrderBolt(final String guid, final List<String> outputFields, final Boolean ordered) {
		super(guid, outputFields);
		this.ordered = ordered;
	}

	@Override
	public void executeLogic(final WorkberchTuple input, final BasicOutputCollector collector, final boolean lastValues, final String uuid) {
		processReceivedTuple(input, collector, lastValues, uuid);

		if (lastValues && !ordered) {
			emitAllSavedTuplesInOrder((CartesianIndex) input.getValues().get(INDEX_FIELD), collector, uuid);
		}
	}

	abstract public void executeOrdered(final WorkberchTuple input, final BasicOutputCollector collector, final boolean lastValues, final String uuid);

}
