package com.fasterxml.jackson.core.write;

import java.io.*;

import com.fasterxml.jackson.core.*;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.core.filter.FilteringGeneratorDelegate;
import com.fasterxml.jackson.core.filter.JsonPointerBasedFilter;
import com.fasterxml.jackson.core.filter.TokenFilter.Inclusion;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.json.UTF8JsonGenerator;

import static org.junit.jupiter.api.Assertions.*;

class UTF8GeneratorTest extends JUnit5TestBase
{
    private final JsonFactory JSON_F = new JsonFactory();

    private final JsonFactory JSON_MAX_NESTING_1 = JsonFactory.builder()
            .streamWriteConstraints(StreamWriteConstraints.builder().maxNestingDepth(1).build())
            .build();

    @Test
    void utf8Issue462() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        IOContext ioc = testIOContext();
        JsonGenerator gen = new UTF8JsonGenerator(ioc, 0, null, bytes, '"');
        String str = "Natuurlijk is alles gelukt en weer een tevreden klant\uD83D\uDE04";
        int length = 4000 - 38;

        for (int i = 1; i <= length; ++i) {
            gen.writeNumber(1);
        }
        gen.writeString(str);
        gen.flush();
        gen.close();

        // Also verify it's parsable?
        JsonParser p = JSON_F.createParser(bytes.toByteArray());
        for (int i = 1; i <= length; ++i) {
            assertToken(JsonToken.VALUE_NUMBER_INT, p.nextToken());
            assertEquals(1, p.getIntValue());
        }
        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(str, p.getText());
        assertNull(p.nextToken());
        p.close();
    }

    @Test
    void nestingDepthWithSmallLimit() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JsonGenerator gen = JSON_MAX_NESTING_1.createGenerator(bytes)) {
            gen.writeStartObject();
            gen.writeFieldName("array");
            gen.writeStartArray();
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException sce) {
            String expected = "Document nesting depth (2) exceeds the maximum allowed (1, from `StreamWriteConstraints.getMaxNestingDepth()`)";
            assertEquals(expected, sce.getMessage());
        }
    }

    @Test
    void nestingDepthWithSmallLimitNestedObject() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JsonGenerator gen = JSON_MAX_NESTING_1.createGenerator(bytes)) {
            gen.writeStartObject();
            gen.writeFieldName("object");
            gen.writeStartObject();
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException sce) {
            String expected = "Document nesting depth (2) exceeds the maximum allowed (1, from `StreamWriteConstraints.getMaxNestingDepth()`)";
            assertEquals(expected, sce.getMessage());
        }
    }

    // for [core#115]
    @Test
    void surrogatesWithRaw() throws Exception
    {
        final String VALUE = q("\ud83d\ude0c");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonGenerator g = JSON_F.createGenerator(out);
        g.writeStartArray();
        g.writeRaw(VALUE);
        g.writeEndArray();
        g.close();

        final byte[] JSON = out.toByteArray();

        JsonParser jp = JSON_F.createParser(JSON);
        assertToken(JsonToken.START_ARRAY, jp.nextToken());
        assertToken(JsonToken.VALUE_STRING, jp.nextToken());
        String str = jp.getText();
        assertEquals(2, str.length());
        assertEquals((char) 0xD83D, str.charAt(0));
        assertEquals((char) 0xDE0C, str.charAt(1));
        assertToken(JsonToken.END_ARRAY, jp.nextToken());
        jp.close();
    }

    @Test
    void filteringWithEscapedChars() throws Exception
    {
        final String SAMPLE_WITH_QUOTES = "\b\t\f\n\r\"foo\"\u0000";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        @SuppressWarnings("resource")
        JsonGenerator g = JSON_F.createGenerator(out);

        FilteringGeneratorDelegate gen = new FilteringGeneratorDelegate(g,
                new JsonPointerBasedFilter("/escapes"),
                Inclusion.INCLUDE_ALL_AND_PATH,
                false // multipleMatches
        );

        //final String JSON = "{'a':123,'array':[1,2],'escapes':'\b\t\f\n\r\"foo\"\u0000'}";

        gen.writeStartObject();

        gen.writeFieldName("a");
        gen.writeNumber(123);

        gen.writeFieldName("array");
        gen.writeStartArray();
        gen.writeNumber((short) 1);
        gen.writeNumber((short) 2);
        gen.writeEndArray();

        gen.writeFieldName("escapes");

        final byte[] raw = utf8Bytes(SAMPLE_WITH_QUOTES);
        gen.writeUTF8String(raw, 0, raw.length);

        gen.writeEndObject();
        gen.close();

        JsonParser p = JSON_F.createParser(out.toByteArray());

        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.FIELD_NAME, p.nextToken());
        assertEquals("escapes", p.currentName());

        assertToken(JsonToken.VALUE_STRING, p.nextToken());
        assertEquals(SAMPLE_WITH_QUOTES, p.getText());
        assertToken(JsonToken.END_OBJECT, p.nextToken());
        assertNull(p.nextToken());
        p.close();
    }

    /*
     * Ce test verifie que le constructor de UTF8JsonGenerator initialise correctement les proprietés les plus importants 
     * a partir des paramètres données.
     */
    @Test
    void testUTF8JsonGeneratorConstructor() throws Exception {
        // arrange
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        IOContext ctxt = testIOContext();
        int outputOffset = 0;
        String test = "testing!";
        byte[] buffer = ctxt.allocWriteEncodingBuffer();

        // act
        JsonGenerator generator = new UTF8JsonGenerator(ctxt, 0, null, output, '"', buffer, outputOffset, true);
        generator.writeString(test);
        generator.flush();
        generator.close();
        JsonParser parser = JSON_F.createParser(output.toByteArray());

        // assert
        assertToken(JsonToken.VALUE_STRING, parser.nextToken());
        assertEquals(test, parser.getText());
        assertNull(parser.nextToken());
        parser.close();
        assertEquals(generator.getOutputBuffered(), outputOffset);
     }

    /*
     * Ce test vérifique que writeRaw() ne parse pas un caractère invalide comme text. 
     */
    
    @Test
    void writeRawInvalidCharTest() throws Exception
    {
        // arrange
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JsonGenerator generator = JSON_F.createGenerator(bytes);
        String test = "ã"; // comme une pauvre brésilienne c'est dommage que ã ne soit pas valide :(
        // act
        generator.writeRaw(test.charAt(0));
        generator.flush();
        generator.close();
        // assert
        JsonParser parser = JSON_F.createParser(bytes.toByteArray());
        assertEquals(null, parser.getText());
    }

    /*
     * Ce test vérifique que writeRaw() ne parse pas un caractère invalide dans un array comme text. 
     */
    @Test
    void writeRawInvalidArrayTest() throws Exception
    {
        // arrange
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        JsonGenerator generator = JSON_F.createGenerator(bytes);
        String test = "ã test õ"; 
        // act
        generator.writeRaw(test.toCharArray(),0,8);
        generator.flush();
        generator.close();
        // assert
        JsonParser parser = JSON_F.createParser(bytes.toByteArray());
        assertEquals(null, parser.getText());
    }
    
}
