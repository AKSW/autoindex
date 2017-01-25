package org.aksw.simba.Initializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.aksw.simba.dataformat.MapperDataStruct;
import org.aksw.simba.dataformat.NGramStruct;
import org.aksw.simba.dataformat.ResultDataStruct;

/* This is the class for activating the nodes initiiated from the mappings.  */
public class initializer implements InitializerInterface {

	/* Initiating the list of activated nodes. */
	private List<ResultDataStruct> propagator = new ArrayList<ResultDataStruct>();

	@Override
	public void initiate(Map<Integer, MapperDataStruct> urimaps,
			List<NGramStruct> ngrams) {
		List<String> URI;
		List<String> LabelList;
		List<Double> EnergyScoreList;
		for (Map.Entry<Integer, MapperDataStruct> entry : urimaps.entrySet()) {
			int index = entry.getKey();
			URI = entry.getValue().getURIList();
			EnergyScoreList = entry.getValue().getEnergyScore();
			LabelList = entry.getValue().getLabelList();

			/*
			 * For each URI in the list of mappings corresponding to respective
			 * ngrams, It activates the node and inserts in the list
			 */
			for (int i = 0; i < URI.size(); i++) {

				/* Calculating the explaination score */
				Double explaination_score = (double) (ngrams.get(index)
						.getEnd() - ngrams.get(index).getBegin() + 1);

				/* Activating the new node */
				ResultDataStruct result = new ResultDataStruct(URI.get(i),
						explaination_score, EnergyScoreList.get(i), ngrams.get(
								index).getBegin(), ngrams.get(index).getEnd());
				result.setActivation("Initial");
				/* Adding to activated node to the list */
				result.setImage("No image");
				this.propagator.add(result);
			}
		}
	}

	/* Retrieving the activation list */
	@Override
	public List<ResultDataStruct> getResultsList() {
		return this.propagator;
	}

}
