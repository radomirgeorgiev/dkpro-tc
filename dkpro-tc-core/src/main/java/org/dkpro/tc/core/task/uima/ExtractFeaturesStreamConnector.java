/*******************************************************************************
 * Copyright 2016
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.dkpro.tc.core.task.uima;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.io.FileUtils;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.descriptor.ExternalResource;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Level;
import org.dkpro.tc.api.features.Feature;
import org.dkpro.tc.api.features.FeatureExtractorResource_ImplBase;
import org.dkpro.tc.api.features.Instance;
import org.dkpro.tc.api.type.JCasId;
import org.dkpro.tc.core.Constants;
import org.dkpro.tc.core.io.DataStreamWriter;
import org.dkpro.tc.core.task.ExtractFeaturesTask;
import org.dkpro.tc.core.util.TaskUtils;
import org.dkpro.tc.fstore.filter.AdaptTestToTrainingFeaturesFilter;
import org.dkpro.tc.fstore.filter.FeatureFilter;
import org.dkpro.tc.fstore.filter.FeatureStoreFilter;

/**
 * UIMA analysis engine that is used in the {@link ExtractFeaturesTask} to apply
 * the feature extractors on each CAS.
 */
public class ExtractFeaturesStreamConnector extends ConnectorBase {

	/**
	 * Directory in which the extracted features will be stored
	 */
	public static final String PARAM_OUTPUT_DIRECTORY = "outputDirectory";

	@ConfigurationParameter(name = PARAM_OUTPUT_DIRECTORY, mandatory = true)
	private File outputDirectory;

	/**
	 * Whether an ID should be added to each instance in the feature file
	 */
	public static final String PARAM_ADD_INSTANCE_ID = "addInstanceId";
	@ConfigurationParameter(name = PARAM_ADD_INSTANCE_ID, mandatory = true, defaultValue = "true")
	private boolean addInstanceId;

	@ExternalResource(key = PARAM_FEATURE_EXTRACTORS, mandatory = true)
	protected FeatureExtractorResource_ImplBase[] featureExtractors;

	@ConfigurationParameter(name = PARAM_FEATURE_FILTERS, mandatory = true)
	private String[] featureFilters;

	@ConfigurationParameter(name = PARAM_DATA_WRITER_CLASS, mandatory = true)
	private String dataWriterClass;

	@ConfigurationParameter(name = PARAM_LEARNING_MODE, mandatory = true, defaultValue = Constants.LM_SINGLE_LABEL)
	private String learningMode;

	@ConfigurationParameter(name = PARAM_FEATURE_MODE, mandatory = true, defaultValue = Constants.FM_DOCUMENT)
	private String featureMode;

	@ConfigurationParameter(name = PARAM_DEVELOPER_MODE, mandatory = true, defaultValue = "false")
	private boolean developerMode;

	@ConfigurationParameter(name = PARAM_APPLY_WEIGHTING, mandatory = true, defaultValue = "false")
	private boolean applyWeighting;

	@ConfigurationParameter(name = PARAM_IS_TESTING, mandatory = true)
	private boolean isTesting;

	/*
	 * Default value as String; see
	 * https://code.google.com/p/dkpro-tc/issues/detail?id=200#c9
	 */
	@ConfigurationParameter(name = PARAM_USE_SPARSE_FEATURES, mandatory = true, defaultValue = "false")
	private boolean useSparseFeatures;

	DataStreamWriter dsw;

	Set<String> featureNames;
	Set<String> uniqueOutcomes;

	File jsonTempFile;

	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);

		featureNames = new HashSet<>();
		uniqueOutcomes = new HashSet<>();

		if (featureExtractors.length == 0) {
			context.getLogger().log(Level.SEVERE, "No feature extractors have been defined.");
			throw new ResourceInitializationException();
		}

		jsonTempFile = new File(outputDirectory, "json.txt");

		try {
			dsw = (DataStreamWriter) Class.forName("org.dkpro.tc.ml.weka.writer.WekaStreamDataWriter").newInstance();
			dsw.init(jsonTempFile);
		} catch (Exception e) {
			throw new ResourceInitializationException(e);
		}
	}

	@Override
	public void process(JCas jcas) throws AnalysisEngineProcessException {
		getLogger().log(Level.INFO, "--- feature extraction for CAS with id ["
				+ JCasUtil.selectSingle(jcas, JCasId.class).getId() + "] ---");

		List<Instance> instances = new ArrayList<Instance>();
		try {
			if (featureMode.equals(Constants.FM_SEQUENCE)) {
				instances = TaskUtils.getMultipleInstancesSequenceMode(featureExtractors, jcas, addInstanceId,
						useSparseFeatures);
			} else if (featureMode.equals(Constants.FM_UNIT)) {
				instances = TaskUtils.getMultipleInstancesUnitMode(featureExtractors, jcas, addInstanceId,
						useSparseFeatures);
			} else {
				instances.add(TaskUtils.getSingleInstance(featureMode, featureExtractors, jcas, developerMode,
						addInstanceId, useSparseFeatures));
			}

			dsw.write(instances);

		} catch (Exception e1) {
			throw new AnalysisEngineProcessException(e1);
		}

		for (Instance i : instances) {
			for (Feature f : i.getFeatures()) {
				featureNames.add(f.getName());
			}
			for (String o : i.getOutcomes()) {
				uniqueOutcomes.add(o);
			}
		}
	}

	@Override
	public void collectionProcessComplete() throws AnalysisEngineProcessException {
		super.collectionProcessComplete();

		try {
			dsw.close();

			// FIXME: How to implement filtering
			applyFilter(jsonTempFile);

			// write feature names file if in training mode
			writeOutcomes();

			if (!isTesting) {
				writeFeatureNames();
			} else {
				applyFeatureNameFilter();
			}

			dsw.transform(outputDirectory, useSparseFeatures, learningMode, applyWeighting);
		} catch (Exception e) {
			throw new AnalysisEngineProcessException(e);
		}

	}

	private void writeOutcomes() throws AnalysisEngineProcessException {
		File outcomesFile = new File(outputDirectory, Constants.FILENAME_OUTCOMES);
		try {
			FileUtils.writeLines(outcomesFile, "utf-8", uniqueOutcomes);
		} catch (IOException e) {
			throw new AnalysisEngineProcessException(e);
		}
	}

	private void applyFeatureNameFilter() throws AnalysisEngineProcessException {
		File featureNamesFile = new File(outputDirectory, Constants.FILENAME_FEATURES);
		TreeSet<String> trainFeatureNames;
		try {
			trainFeatureNames = new TreeSet<>(FileUtils.readLines(featureNamesFile));
		} catch (IOException e) {
			throw new AnalysisEngineProcessException(e);
		}

		AdaptTestToTrainingFeaturesFilter filter = new AdaptTestToTrainingFeaturesFilter();
		// if feature space from training set and test set differs, apply the
		// filter
		// to keep only features seen during training
		if (!trainFeatureNames.equals(featureNames)) {
			filter.setFeatureNames(trainFeatureNames);
			// filter.applyFilter(featureStore);
			// FIXME: how to deal with applyFilter?
		}
	}

	private void writeFeatureNames() throws AnalysisEngineProcessException {
		try {
			FileUtils.writeLines(new File(outputDirectory, Constants.FILENAME_FEATURES), featureNames);
		} catch (IOException e) {
			throw new AnalysisEngineProcessException(e);
		}
	}

	private void applyFilter(File jsonTempFile) throws AnalysisEngineProcessException {
		// apply filters that influence the whole feature store
		// filters are applied in the order that they appear as parameters
		for (String filterString : featureFilters) {
			FeatureFilter filter;
			try {
				filter = (FeatureFilter) Class.forName(filterString).newInstance();

				if (filter.isApplicableForTraining() && !isTesting || filter.isApplicableForTesting() && isTesting) {
					filter.applyFilter(jsonTempFile);
				}
			} catch (Exception e) {
				throw new AnalysisEngineProcessException(e);
			}
		}
	}
}