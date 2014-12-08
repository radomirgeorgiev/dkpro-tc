/**
 * Copyright 2014
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package de.tudarmstadt.ukp.dkpro.tc.ml.uima;

import static de.tudarmstadt.ukp.dkpro.tc.core.Constants.MODEL_FEATURE_EXTRACTORS;
import static de.tudarmstadt.ukp.dkpro.tc.core.Constants.MODEL_META;
import static de.tudarmstadt.ukp.dkpro.tc.core.Constants.MODEL_PARAMETERS;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.ExternalResourceFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.Resource;
import org.apache.uima.resource.ResourceInitializationException;

import de.tudarmstadt.ukp.dkpro.tc.api.type.TextClassificationOutcome;
import de.tudarmstadt.ukp.dkpro.tc.core.Constants;
import de.tudarmstadt.ukp.dkpro.tc.core.ml.SaveModelConnector_ImplBase;
import de.tudarmstadt.ukp.dkpro.tc.core.ml.TCMachineLearningAdapter;
import de.tudarmstadt.ukp.dkpro.tc.fstore.simple.DenseFeatureStore;

public class TcAnnotator
	extends JCasAnnotator_ImplBase
{
	
    public static final String PARAM_TC_MODEL_LOCATION = "tcModel";
    @ConfigurationParameter(name = PARAM_TC_MODEL_LOCATION, mandatory = true)
    protected File tcModelLocation;
    
    private String learningMode = Constants.LM_SINGLE_LABEL;
    private String featureMode = Constants.FM_DOCUMENT;
    
//    private List<FeatureExtractorResource_ImplBase> featureExtractors;
    private List<String> featureExtractors;
    private List<Object> parameters;
    
    private TCMachineLearningAdapter mlAdapter;

	@Override
	public void initialize(UimaContext context)
			throws ResourceInitializationException
	{		
		super.initialize(context);
        
		try {
			mlAdapter = (TCMachineLearningAdapter) Class.forName(FileUtils.readFileToString(new File(tcModelLocation, MODEL_META))).newInstance();
		} catch (InstantiationException e) {
			throw new ResourceInitializationException(e);
		} catch (IllegalAccessException e) {
			throw new ResourceInitializationException(e);
		} catch (ClassNotFoundException e) {
			throw new ResourceInitializationException(e);
		} catch (IOException e) {
			throw new ResourceInitializationException(e);
		}
		
        parameters = new ArrayList<>();
        try {
			for (String parameter : FileUtils.readLines(new File(tcModelLocation, MODEL_PARAMETERS))) {
				if (!parameter.startsWith("#")) {
					String[] parts = parameter.split("=");
					parameters.add(parts[0]);					
					parameters.add(parts[1]);					
				}
			}
		} catch (IOException e) {
			throw new ResourceInitializationException(e);
		}
        featureExtractors = new ArrayList<>();
        try {
            for (String featureExtractor : FileUtils.readLines(new File(tcModelLocation, MODEL_FEATURE_EXTRACTORS))) {
    			featureExtractors.add(featureExtractor);
    		}
        } catch (IOException e) {
			throw new ResourceInitializationException(e);
		}

        
//        featureExtractors = new ArrayList<>();
//        try {
//			for (String featureExtractor : FileUtils.readLines(new File(tcModelLocation, "features.txt"))) {
//				featureExtractors.add(
//						(FeatureExtractorResource_ImplBase) Class.forName(featureExtractor).newInstance()
//				);
//			}
//		} catch (InstantiationException e) {
//			throw new ResourceInitializationException(e);
//		} catch (IllegalAccessException e) {
//			throw new ResourceInitializationException(e);
//		} catch (ClassNotFoundException e) {
//			throw new ResourceInitializationException(e);
//		} catch (IOException e) {
//			throw new ResourceInitializationException(e);
//		}
	}

	@Override
	public void process(JCas jcas) throws AnalysisEngineProcessException {
		
		// we need an outcome annotation present
		TextClassificationOutcome outcome = new TextClassificationOutcome(jcas);
		outcome.setOutcome("");
		outcome.addToIndexes();
		
		// create new UIMA annotator in order to separate the parameter spaces
		// this annotator will get initialized with its own set of parameters loaded from the model
		try {
			AnalysisEngineDescription connector = getSaveModelConnector(
			        parameters, tcModelLocation.getAbsolutePath(), mlAdapter.getDataWriterClass(learningMode).toString(), learningMode, featureMode,
			        DenseFeatureStore.class.getName(), featureExtractors.toArray(new String[0]));
			AnalysisEngine engine = AnalysisEngineFactory.createEngine(connector);
			
			// process and classify
			engine.process(jcas);
		} catch (ResourceInitializationException e) {
			throw new AnalysisEngineProcessException(e);
		}
		
		System.out.println(JCasUtil.selectSingle(jcas, TextClassificationOutcome.class).getOutcome());
	}
	
	   /**
     * @param featureExtractorClassNames @return A fully configured feature extractor connector
     * @throws ResourceInitializationException
     */
    private AnalysisEngineDescription getSaveModelConnector(List<Object> parameters,
            String outputPath, String dataWriter, String learningMode, String featureMode,
            String featureStore, String... featureExtractorClassNames)
            throws ResourceInitializationException
    {
        // convert parameters to string as external resources only take string parameters
        List<Object> convertedParameters = new ArrayList<Object>();
        if (parameters != null) {
            for (Object parameter : parameters) {
                convertedParameters.add(parameter.toString());
            }
        }
        else {
            parameters = new ArrayList<Object>();
        }

        List<ExternalResourceDescription> extractorResources = new ArrayList<ExternalResourceDescription>();
        for (String featureExtractor : featureExtractorClassNames) {
            try {
                extractorResources.add(ExternalResourceFactory.createExternalResourceDescription(
                        Class.forName(featureExtractor).asSubclass(Resource.class),
                        convertedParameters.toArray()));
            }
            catch (ClassNotFoundException e) {
                throw new ResourceInitializationException(e);
            }
        }

        // add the rest of the necessary parameters with the correct types
        parameters.addAll(Arrays.asList(
        		TcAnnotator.PARAM_TC_MODEL_LOCATION, tcModelLocation, 
        		SaveModelConnector_ImplBase.PARAM_OUTPUT_DIRECTORY, outputPath, 
        		SaveModelConnector_ImplBase.PARAM_DATA_WRITER_CLASS, dataWriter,
        		SaveModelConnector_ImplBase.PARAM_LEARNING_MODE, learningMode,
        		SaveModelConnector_ImplBase.PARAM_FEATURE_EXTRACTORS, extractorResources,
        		SaveModelConnector_ImplBase.PARAM_FEATURE_FILTERS, null,
        		SaveModelConnector_ImplBase.PARAM_IS_TESTING, true,
        		SaveModelConnector_ImplBase.PARAM_FEATURE_MODE, featureMode,
        		SaveModelConnector_ImplBase.PARAM_FEATURE_STORE_CLASS, featureStore
        ));

        return AnalysisEngineFactory.createEngineDescription(
        		mlAdapter.getSaveModelConnectorClass(),
                parameters.toArray());
    }

}