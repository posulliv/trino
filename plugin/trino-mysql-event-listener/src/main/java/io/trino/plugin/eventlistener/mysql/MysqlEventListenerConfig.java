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
package io.trino.plugin.eventlistener.mysql;

import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;
import io.airlift.configuration.ConfigSecuritySensitive;
import jakarta.validation.constraints.NotNull;

public class MysqlEventListenerConfig
{
    private String url;
    private String user;
    private String password;

    @NotNull
    public String getUrl()
    {
        return url;
    }

    @Config("mysql-event-listener.db.url")
    @ConfigDescription("MySQL database url")
    public MysqlEventListenerConfig setUrl(String url)
    {
        this.url = url;
        return this;
    }

    public String getUser()
    {
        return user;
    }

    @Config("mysql-event-listener.db.user")
    @ConfigDescription("MySQL database user name")
    public MysqlEventListenerConfig setUser(String configUser)
    {
        this.user = configUser;
        return this;
    }

    public String getPassword()
    {
        return password;
    }

    @ConfigSecuritySensitive
    @Config("mysql-event-listener.db.password")
    @ConfigDescription("MySQL database password")
    public MysqlEventListenerConfig setPassword(String configPassword)
    {
        this.password = configPassword;
        return this;
    }
}
