/*
 * Copyright 2012-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.diagnostics;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Utility to trigger {@link FailureAnalyzer} and {@link FailureAnalysisReporter}
 * instances loaded from {@code spring.factories}.
 * <p>
 * A {@code FailureAnalyzer} that requires access to the {@link BeanFactory} in order to
 * perform its analysis can implement {@code BeanFactoryAware} to have the
 * {@code BeanFactory} injected prior to {@link FailureAnalyzer#analyze(Throwable)} being
 * called.
 *
 * @author Andy Wilkinson
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @since 1.4.0
 */
public final class FailureAnalyzers {

	private static final Log log = LogFactory.getLog(FailureAnalyzers.class);

	private final ClassLoader classLoader;

	private final List<FailureAnalyzer> analyzers;

	public FailureAnalyzers(ConfigurableApplicationContext context) {
		this.classLoader = context.getClassLoader();
		this.analyzers = loadFailureAnalyzers(this.classLoader);
		prepareFailureAnalyzers(this.analyzers, context);
	}

	/**
	 * Analyze and report the specified {@code failure}.
	 *
	 * @param failure the failure to analyze
	 * @return {@code true} if the failure was handled
	 */
	public boolean analyzeAndReport(Throwable failure) {
		FailureAnalysis analysis = analyze(failure, this.analyzers);
		return report(analysis, this.classLoader);
	}

	/**
	 * Analyze and report the specified {@code failure}.
	 *
	 * @param failure the failure to analyze
	 * @param classLoader the classloader to use
	 * @param context the context to use
	 * @return {@code true} if the failure was handled
	 * @deprecated in favour of {@link #analyzeAndReport(Throwable)}
	 */
	@Deprecated
	public static boolean analyzeAndReport(Throwable failure, ClassLoader classLoader,
			ConfigurableApplicationContext context) {
		List<FailureAnalyzer> analyzers = loadFailureAnalyzers(classLoader);
		prepareFailureAnalyzers(analyzers, context);
		FailureAnalysis analysis = analyze(failure, analyzers);
		return report(analysis, classLoader);
	}

	private static List<FailureAnalyzer> loadFailureAnalyzers(ClassLoader classLoader) {
		List<String> analyzerNames = SpringFactoriesLoader
				.loadFactoryNames(FailureAnalyzer.class, classLoader);
		List<FailureAnalyzer> analyzers = new ArrayList<FailureAnalyzer>();
		for (String analyzerName : analyzerNames) {
			try {
				Constructor<?> constructor = ClassUtils.forName(analyzerName, classLoader)
						.getDeclaredConstructor();
				ReflectionUtils.makeAccessible(constructor);
				analyzers.add((FailureAnalyzer) constructor.newInstance());
			}
			catch (Throwable ex) {
				log.trace("Failed to load " + analyzerName, ex);
			}
		}
		AnnotationAwareOrderComparator.sort(analyzers);
		return analyzers;
	}

	private static void prepareFailureAnalyzers(List<FailureAnalyzer> analyzers,
			ConfigurableApplicationContext context) {
		for (FailureAnalyzer analyzer : analyzers) {
			prepareAnalyzer(context, analyzer);
		}
	}

	private static FailureAnalysis analyze(Throwable failure,
			List<FailureAnalyzer> analyzers) {
		for (FailureAnalyzer analyzer : analyzers) {
			FailureAnalysis analysis = analyzer.analyze(failure);
			if (analysis != null) {
				return analysis;
			}
		}
		return null;
	}

	private static void prepareAnalyzer(ConfigurableApplicationContext context,
			FailureAnalyzer analyzer) {
		if (analyzer instanceof BeanFactoryAware) {
			((BeanFactoryAware) analyzer).setBeanFactory(context.getBeanFactory());
		}
	}

	private static boolean report(FailureAnalysis analysis,
			ClassLoader classLoader) {
		List<FailureAnalysisReporter> reporters = SpringFactoriesLoader
				.loadFactories(FailureAnalysisReporter.class, classLoader);
		if (analysis == null || reporters.isEmpty()) {
			return false;
		}
		for (FailureAnalysisReporter reporter : reporters) {
			reporter.report(analysis);
		}
		return true;
	}

}
