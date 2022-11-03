/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.avro;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Schema parser for JSON formatted schemata. This initial implementation simply
 * delegates to the {@link Schema.Parser} class, though it should be refactored
 * out of there.
 *
 * <p>
 * Note: this class is intentionally not available via the Java
 * {@link java.util.ServiceLoader}, as its use is hardcoded as fallback when no
 * service exists. This enables users to reliably override the standard JSON
 * parser as well.
 * </p>
 */
public class JsonSchemaParser implements FormattedSchemaParser {
  /**
   * <p>
   * Parse a schema written in the internal (JSON) format without any validations.
   * </p>
   *
   * <p>
   * Using this method is only safe if used to parse a write schema (i.e., a
   * schema used to read Avro data). Other usages, for example by generated Avro
   * code, can cause interoperability problems.
   * </p>
   *
   * <p>
   * Use with care and sufficient testing!
   * </p>
   *
   * @param fragments one or more strings making up the schema (some schemata
   *                  exceed the compiler limits)
   * @return the parsed schema
   */
  public static Schema parseInternal(String... fragments) {
    StringBuilder buffer = new StringBuilder();
    for (String fragment : fragments) {
      buffer.append(fragment);
    }
    return new JsonSchemaParser().parse(new ArrayList<>(), buffer, true);
  }

  @Override
  public Schema parse(Collection<Schema> schemas, URI baseUri, CharSequence formattedSchema)
      throws IOException, SchemaParseException {
    return parse(schemas, formattedSchema, false);
  }

  private Schema parse(Collection<Schema> schemas, CharSequence formattedSchema, boolean skipValidation)
      throws SchemaParseException {
    // TODO: refactor JSON parsing out of the Schema class
    Schema.Parser parser;
    if (skipValidation) {
      parser = new Schema.Parser(Schema.NameValidator.NO_VALIDATION);
      parser.setValidateDefaults(false);
    } else {
      parser = new Schema.Parser();
    }
    if (schemas != null) {
      parser.addTypes(schemas);
    }
    Schema schema = parser.parse(formattedSchema.toString());
    if (schemas != null) {
      schemas.addAll(parser.getTypes().values());
    }
    return schema;
  }
}
