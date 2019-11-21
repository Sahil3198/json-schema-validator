/*
 * Copyright (c) 2016 Network New Technologies Inc.
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

package com.networknt.schema;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RunWith(Parameterized.class)
public class CustomMetaSchemaTest {
    @Parameterized.Parameters
    public static Collection<?> parameters() {
      return Arrays.asList(new Object[][] {
         { JsonMetaSchema.getV4(), SpecVersion.VersionFlag.V4 },
         { JsonMetaSchema.getV6(), SpecVersion.VersionFlag.V6 },
         { JsonMetaSchema.getV7(), SpecVersion.VersionFlag.V7 },
         { JsonMetaSchema.getV201909(), SpecVersion.VersionFlag.V201909 }
      });
    }
    
    private final JsonMetaSchema baseMetaSchema;
    private final SpecVersion.VersionFlag specVersion;
    
    public CustomMetaSchemaTest(final JsonMetaSchema baseMetaSchema, final SpecVersion.VersionFlag specVersion) {
        this.baseMetaSchema = baseMetaSchema;
        this.specVersion = specVersion;
    }

    /**
     * Introduces the keyword "enumNames".
     * 
     * The keyword is used together with "enum" and must have the same length.
     * 
     * This keyword always produces a warning during validation - 
     * so it makes no sense in reality but should be useful for demonstration / testing purposes. 
     * 
     * @author klaskalass
     *
     */
    public static class EnumNamesKeyword extends AbstractKeyword {

        private static final class Validator extends AbstractJsonValidator {
            private final List<String> enumValues;
            private final List<String> enumNames;
            
            private Validator(String keyword, List<String> enumValues, List<String> enumNames) {
                super(keyword);
                if (enumNames.size() != enumValues.size()) {
                    throw new IllegalArgumentException("enum and enumNames need to be of same length");
                }
                this.enumNames = enumNames;
                this.enumValues = enumValues;
            }

            @Override
            public Set<ValidationMessage> validate(JsonNode node, JsonNode rootNode, String at) {
                String value = node.asText();
                int idx = enumValues.indexOf(value);
                if (idx < 0) {
                    throw new IllegalArgumentException("value not found in enum. value: " + value  + " enum: " + enumValues);
                }
                String valueName = enumNames.get(idx);
                return fail(CustomErrorMessageType.of("tests.example.enumNames", new MessageFormat("{0}: enumName is {1}")), at, valueName);
            }
        }

        
        public EnumNamesKeyword() {
            super("enumNames");
        }
        
        @Override
        public JsonValidator newValidator(String schemaPath, JsonNode schemaNode, JsonSchema parentSchema,
                ValidationContext validationContext) throws JsonSchemaException, Exception {
            /*
             * You can access the schema node here to read data from your keyword
             */
            if (!schemaNode.isArray()) {
                throw new JsonSchemaException("Keyword enumNames needs to receive an array");
            }
            JsonNode parentSchemaNode = parentSchema.getSchemaNode();
            if (!parentSchemaNode.has("enum")) {
                throw new JsonSchemaException("Keyword enumNames needs to have a sibling enum keyword");
            }
            JsonNode enumSchemaNode = parentSchemaNode.get("enum");
            
            return new Validator(getValue(), readStringList(enumSchemaNode), readStringList(schemaNode));
        }
        
        private List<String> readStringList(JsonNode node) {
            if (!node.isArray()) {
                throw new JsonSchemaException("Keyword enum needs to receive an array");
            }
            ArrayList<String> result = new ArrayList<String>(node.size());
            for (JsonNode child : node) {
                result.add(child.asText());
            }
            return result;
        }
    }

    @Test
    public void customMetaSchemaWithIgnoredKeyword() throws JsonProcessingException, IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        final JsonMetaSchema metaSchema = JsonMetaSchema
                .builder("https://github.com/networknt/json-schema-validator/tests/schemas/example01#", this.baseMetaSchema)
                // Generated UI uses enumNames to render Labels for enum values
                .addKeyword(new EnumNamesKeyword())
                .build();
        
        final JsonSchemaFactory validatorFactory = JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(this.specVersion))
                .addMetaSchema(metaSchema)
                .build();
        final JsonSchema schema = validatorFactory.getSchema("{\n" + 
                "  \"$schema\":\n" + 
                "    \"https://github.com/networknt/json-schema-validator/tests/schemas/example01#\",\n" + 
                "  \"enum\": [\"foo\", \"bar\"],\n" + 
                "  \"enumNames\": [\"Foo !\", \"Bar !\"]\n" + 
                "}");
        
        Set<ValidationMessage> messages = schema.validate(objectMapper.readTree("\"foo\""));
        assertEquals(1, messages.size());
        
        ValidationMessage message = messages.iterator().next();
        assertEquals("$: enumName is Foo !", message.getMessage());
    }
}
