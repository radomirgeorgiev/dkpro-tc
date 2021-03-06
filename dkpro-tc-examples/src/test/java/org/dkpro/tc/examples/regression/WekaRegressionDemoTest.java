/**
 * Copyright 2018
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
package org.dkpro.tc.examples.regression;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.dkpro.lab.task.ParameterSpace;
import org.dkpro.tc.examples.TestCaseSuperClass;
import org.dkpro.tc.examples.util.ContextMemoryReport;
import org.dkpro.tc.ml.report.util.Tc2LtlabEvalConverter;
import org.dkpro.tc.ml.weka.task.WekaTestTask;
import org.junit.Before;
import org.junit.Test;

import de.unidue.ltl.evaluation.core.EvaluationData;
import de.unidue.ltl.evaluation.measures.regression.MeanAbsoluteError;
import weka.core.SerializationHelper;

/**
 * This test just ensures that the experiment runs without throwing
 * any exception.
 */
public class WekaRegressionDemoTest extends TestCaseSuperClass
{
    ParameterSpace pSpace;
    WekaRegressionDemo experiment;
    
    @Before
    public void setup()
        throws Exception
    {
        super.setup();
        
        experiment = new WekaRegressionDemo();
        pSpace = WekaRegressionDemo.getParameterSpace();
    }

    
    @Test
    public void testTrainTest() throws Exception{
        ContextMemoryReport.key = WekaTestTask.class.getName();
        experiment.runTrainTest(pSpace);
        
        //weka offers to calculate this value too - we take weka as "reference" value 
        weka.classifiers.Evaluation eval = (weka.classifiers.Evaluation) SerializationHelper
                .read(new File(ContextMemoryReport.id2outcome.getParent() + "/" +WekaTestTask.evaluationBin).getAbsolutePath());
        double wekaMeanAbsoluteError = eval.meanAbsoluteError();
        
        EvaluationData<Double> data = Tc2LtlabEvalConverter.convertRegressionModeId2Outcome(ContextMemoryReport.id2outcome);
		MeanAbsoluteError mae = new MeanAbsoluteError(data);
        assertEquals(wekaMeanAbsoluteError, mae.getResult(), 0.00001);
    }
}
