/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.plugin.resourcegroups.db;

import com.google.inject.Injector;
import io.airlift.bootstrap.Bootstrap;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.json.JsonModule;
import io.trino.plugin.base.jmx.MBeanServerModule;
import io.trino.plugin.base.jmx.PrefixObjectNameGeneratorModule;
import io.trino.spi.memory.ClusterMemoryPoolManager;
import io.trino.spi.resourcegroups.ResourceGroupConfigurationManager;
import io.trino.spi.resourcegroups.ResourceGroupConfigurationManagerContext;
import io.trino.spi.resourcegroups.ResourceGroupConfigurationManagerFactory;
import org.weakref.jmx.guice.MBeanModule;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static java.util.regex.Matcher.quoteReplacement;

public class DbResourceGroupConfigurationManagerFactory
        implements ResourceGroupConfigurationManagerFactory
{
    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{ENV:([a-zA-Z][a-zA-Z0-9_]*)}");

    @Override
    public String getName()
    {
        return "db";
    }

    @Override
    public ResourceGroupConfigurationManager<?> create(Map<String, String> config, ResourceGroupConfigurationManagerContext context)
    {
        config = replaceEnvironmentVariables(config, System.getenv());
        FlywayMigration.migrate(new ConfigurationFactory(config).build(DbResourceGroupConfig.class));
        Bootstrap app = new Bootstrap(
                new MBeanModule(),
                new MBeanServerModule(),
                new JsonModule(),
                new DbResourceGroupsModule(),
                new PrefixObjectNameGeneratorModule("io.trino.plugin.resourcegroups.db", "trino.plugin.resourcegroups.db"),
                binder -> binder.bind(String.class).annotatedWith(ForEnvironment.class).toInstance(context.getEnvironment()),
                binder -> binder.bind(ClusterMemoryPoolManager.class).toInstance(context.getMemoryPoolManager()));

        Injector injector = app
                .doNotInitializeLogging()
                .setRequiredConfigurationProperties(config)
                .initialize();

        return injector.getInstance(DbResourceGroupConfigurationManager.class);
    }

    private Map<String, String> replaceEnvironmentVariables(Map<String, String> properties, Map<String, String> environment)
    {
        Map<String, String> replaced = new HashMap<>();
        properties.forEach((propertyKey, propertyValue) -> {
            StringBuilder replacedPropertyValue = new StringBuilder();
            Matcher matcher = ENV_PATTERN.matcher(propertyValue);
            while (matcher.find()) {
                String envName = matcher.group(1);
                String envValue = environment.get(envName);
                if (envValue == null) {
                    throw new RuntimeException(format("Configuration property '%s' references unset environment variable '%s'", propertyKey, envName));
                }
                matcher.appendReplacement(replacedPropertyValue, quoteReplacement(envValue));
            }
            matcher.appendTail(replacedPropertyValue);
            replaced.put(propertyKey, replacedPropertyValue.toString());
        });
        return replaced;
    }
}
