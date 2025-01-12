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

import org.apache.avro.util.SchemaResolver;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Class to define a name context, useful to reference schemata with. This
 * allows for the following:
 *
 * <ul>
 * <li>Provide a default namespace for nested contexts, as found for example in
 * JSON based schema definitions.</li>
 * <li>Find schemata by name, including primitives.</li>
 * <li>Collect new named schemata.</li>
 * </ul>
 *
 * <p>
 * Note: this class has no use for most Avro users, but is a key component when
 * implementing a schema parser.
 * </p>
 *
 * @see <a href="https://avro.apache.org/docs/current/specification/">JSON based
 *      schema definition</a>
 **/
public class ParseContext {
  private static final Map<String, Schema.Type> PRIMITIVES = new HashMap<>();

  static {
    PRIMITIVES.put("string", Schema.Type.STRING);
    PRIMITIVES.put("bytes", Schema.Type.BYTES);
    PRIMITIVES.put("int", Schema.Type.INT);
    PRIMITIVES.put("long", Schema.Type.LONG);
    PRIMITIVES.put("float", Schema.Type.FLOAT);
    PRIMITIVES.put("double", Schema.Type.DOUBLE);
    PRIMITIVES.put("boolean", Schema.Type.BOOLEAN);
    PRIMITIVES.put("null", Schema.Type.NULL);
  }

  private static final Set<Schema.Type> NAMED_SCHEMA_TYPES = EnumSet.of(Schema.Type.RECORD, Schema.Type.ENUM,
      Schema.Type.FIXED);
  private final Map<String, Schema> oldSchemas;
  private final Map<String, Schema> newSchemas;
  // Visible for use in JsonSchemaParser
  final NameValidator nameValidator;
  private final String namespace;

  /**
   * Create a {@code ParseContext} for the default/{@code null} namespace, using
   * default name validation for new schemata.
   */
  public ParseContext() {
    this(NameValidator.UTF_VALIDATOR, null);
  }

  /**
   * Create a {@code ParseContext} for the specified namespace, using default name
   * validation for new schemata.
   */
  public ParseContext(String namespace) {
    this(NameValidator.UTF_VALIDATOR, namespace);
  }

  /**
   * Create a {@code ParseContext} for the default/{@code null} namespace, using
   * the specified name validation for new schemata.
   */
  public ParseContext(NameValidator nameValidator) {
    this(nameValidator, null);
  }

  /**
   * Create a {@code ParseContext} for the specified namespace, using the
   * specified name validation for new schemata.
   */
  public ParseContext(NameValidator nameValidator, String namespace) {
    this(nameValidator, new LinkedHashMap<>(), new LinkedHashMap<>(), namespace);
  }

  private ParseContext(NameValidator nameValidator, Map<String, Schema> oldSchemas, Map<String, Schema> newSchemas,
      String namespace) {
    this.nameValidator = nameValidator;
    this.oldSchemas = oldSchemas;
    this.newSchemas = newSchemas;
    this.namespace = notEmpty(namespace) ? namespace : null;
  }

  /**
   * Create a derived context using a different fallback namespace.
   *
   * @param namespace the fallback namespace to resolve names with
   * @return a new context
   */
  public ParseContext namespace(String namespace) {
    return new ParseContext(nameValidator, oldSchemas, newSchemas, namespace);
  }

  /**
   * Return the fallback namespace.
   *
   * @return the namespace
   */
  public String namespace() {
    return namespace;
  }

  /**
   * Tell whether this context contains the given schema.
   *
   * @param schema a schema
   * @return {@code true} if the context contains the schema, {@code false}
   *         otherwise
   */
  @Deprecated
  public boolean contains(Schema schema) {
    String fullName = schema.getFullName();
    return schema.equals(oldSchemas.get(fullName)) || schema.equals(newSchemas.get(fullName));
  }

  /**
   * Tell whether this context contains a schema with the given name.
   *
   * @param name a schema name
   * @return {@code true} if the context contains a schema with this name,
   *         {@code false} otherwise
   */
  public boolean contains(String name) {
    return PRIMITIVES.containsKey(name) || oldSchemas.containsKey(name) || newSchemas.containsKey(name);
  }

  /**
   * Resolve a schema by name. That is:
   *
   * <ul>
   * <li>If {@code name} is a primitive name, return a (new) schema for it</li>
   * <li>If {@code name} contains a dot, resolve the schema by full name only</li>
   * <li>Otherwise: resolve the schema in the current and in the null namespace
   * (the former takes precedence)</li>
   * </ul>
   *
   * Resolving means that the schema is returned if known, and otherwise an
   * unresolved schema (a reference) is returned.
   *
   * @param name the schema name to resolve
   * @return the schema
   * @throws SchemaParseException when the schema does not exist
   */
  public Schema resolve(String name) {
    Schema.Type type = PRIMITIVES.get(name);
    if (type != null) {
      return Schema.create(type);
    }

    String fullName = resolveName(name, namespace);
    Schema schema = getSchema(fullName);
    if (schema == null) {
      schema = getSchema(name);
    }

    return schema != null ? schema : SchemaResolver.unresolvedSchema(fullName);
  }

  private Schema getSchema(String fullName) {
    Schema schema = oldSchemas.get(fullName);
    if (schema == null) {
      schema = newSchemas.get(fullName);
    }
    return schema;
  }

  // Visible for testing
  String resolveName(String name, String space) {
    int lastDot = name.lastIndexOf('.');
    if (lastDot < 0) { // short name
      if (!notEmpty(space)) {
        space = namespace;
      }
      if (notEmpty(space)) {
        return space + "." + name;
      }
    }
    return name;
  }

  /**
   * Return the simplest name that references the same schema in the current
   * namespace. Returns the name without any namespace if it is not a primitive,
   * and the namespace is the current namespace.
   *
   * @param fullName the full schema name
   * @return the simplest name within the current namespace
   */
  public String simpleName(String fullName) {
    int lastDot = fullName.lastIndexOf('.');
    if (lastDot >= 0) {
      String name = fullName.substring(lastDot + 1);
      String space = fullName.substring(0, lastDot);
      if (!PRIMITIVES.containsKey(name) && space.equals(namespace)) {
        // The name is a full name in the current namespace, and cannot be
        // mistaken for a primitive type.
        return name;
      }
    }
    // The special case of the previous comment does not apply.
    return fullName;
  }

  private boolean notEmpty(String str) {
    return str != null && !str.isEmpty();
  }

  /**
   * Put the schema into this context. This is an idempotent operation: it only
   * fails if this context already has a different schema with the same name.
   *
   * <p>
   * Note that although this method works for all types except for arrays, maps
   * and unions, all primitive types have already been defined upon construction.
   * This means you cannot redefine a 'long' with a logical timestamp type.
   * </p>
   *
   * @param schema the schema to put into the context
   */
  public void put(Schema schema) {
    if (!(NAMED_SCHEMA_TYPES.contains(schema.getType()))) {
      throw new AvroTypeException("You can only put a named schema into the context");
    }

    String fullName = requireValidFullName(schema.getFullName());

    Schema alreadyKnownSchema = oldSchemas.get(fullName);
    if (alreadyKnownSchema != null) {
      if (!schema.equals(alreadyKnownSchema)) {
        throw new SchemaParseException("Can't redefine: " + fullName);
      }
    } else {
      Schema previouslyAddedSchema = newSchemas.putIfAbsent(fullName, schema);
      if (previouslyAddedSchema != null && !previouslyAddedSchema.equals(schema)) {
        throw new SchemaParseException("Can't redefine: " + fullName);
      }
    }
  }

  private String requireValidFullName(String fullName) {
    String[] names = fullName.split("\\.");
    for (int i = 0; i < names.length - 1; i++) {
      validateName(names[i], "Namespace part");
    }
    validateName(names[names.length - 1], "Name");
    return fullName;
  }

  private void validateName(String name, String what) {
    NameValidator.Result result = nameValidator.validate(name);
    if (!result.isOK()) {
      throw new SchemaParseException(what + " \"" + name + "\" is invalid: " + result.getErrors());
    }
  }

  public boolean hasNewSchemas() {
    return !newSchemas.isEmpty();
  }

  public void commit() {
    oldSchemas.putAll(newSchemas);
    newSchemas.clear();
  }

  public void rollback() {
    newSchemas.clear();
  }

  /**
   * Return all known types by their fullname.
   *
   * @return a map of all types by their name
   */
  public Map<String, Schema> typesByName() {
    LinkedHashMap<String, Schema> result = new LinkedHashMap<>();
    result.putAll(oldSchemas);
    result.putAll(newSchemas);
    return result;
  }

  public Protocol resolveSchemata(Protocol protocol) {
    protocol.getTypes().forEach(this::put);
    return SchemaResolver.resolve(this, protocol);
  }
}
