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
package io.trino.client;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Used for representing both raw JSON values and spooled metadata.
 */
public interface QueryData
{
    QueryData NULL = new QueryData()
    {
        @Override
        public boolean isNull()
        {
            return true;
        }

        @Override
        public long getRowsCount()
        {
            return 0;
        }
    };

    @JsonIgnore
    boolean isNull();

    long getRowsCount();
}
