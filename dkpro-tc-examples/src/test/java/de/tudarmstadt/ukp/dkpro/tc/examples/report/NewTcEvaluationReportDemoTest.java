/**
 * Copyright 2015
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
package de.tudarmstadt.ukp.dkpro.tc.examples.report;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import de.tudarmstadt.ukp.dkpro.lab.task.ParameterSpace;
import de.tudarmstadt.ukp.dkpro.tc.examples.single.document.ComplexConfigurationSingleDemo;
import de.tudarmstadt.ukp.dkpro.tc.examples.utils.JavaDemosTest_Base;

/**
 * This test just ensures that the experiment runs without throwing
 * any exception.
 */
public class NewTcEvaluationReportDemoTest extends JavaDemosTest_Base
{
    
    NewTcEvaluationReportDemo javaExperiment;
    ParameterSpace pSpace;
    
    @Before
    public void setup()
        throws Exception
    {
        super.setup();
        
        javaExperiment = new NewTcEvaluationReportDemo();
        pSpace = ComplexConfigurationSingleDemo.getParameterSpace();
    }

    @Test
    public void testTrainTest()
        throws Exception
    {
       new NewTcEvaluationReportDemo().runTrainTest(pSpace);
    }
    
    
    @Ignore
    @Test
    public void testCrossValidation()
        throws Exception
    {
       new NewTcEvaluationReportDemo().runCrossvalidation(pSpace);
    }
}