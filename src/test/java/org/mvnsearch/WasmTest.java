package org.mvnsearch;

import org.junit.jupiter.api.Test;
import org.wasmer.Instance;
import org.wasmer.Memory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class WasmTest {

    @Test
    public void testSimpleWasmCall() throws Exception {
        byte[] wasmBytes = readBytesFromClasspath("simple.wasm");
        // Instantiates the WebAssembly module.
        Instance instance = new Instance(wasmBytes);
        // Calls an exported function, and returns an object array.
        Object[] results = instance.exports.getFunction("sum").apply(5, 37);
        Integer sum = (Integer) results[0];
        System.out.println(sum); // 42
        // Drops an instance object pointer which is stored in Rust.
        instance.close();
    }


    @Test
    public void testMemoryOperation() throws Exception {
        Instance instance = new Instance(readBytesFromClasspath("memory.wasm"));
        Integer pointer = (Integer) instance.exports.getFunction("return_hello").apply()[0];
        Memory memory = instance.exports.getMemory("memory");
        ByteBuffer memoryBuffer = memory.buffer();
        byte[] data = new byte[13];
        memoryBuffer.position(pointer);
        memoryBuffer.get(data);
        String result = new String(data);
        assert result.equals("Hello, World!");
        instance.close();
    }

    @Test
    public void testGreet() throws Exception {
        Instance instance = new Instance(readBytesFromClasspath("greet.wasm"));
        Memory memory = instance.exports.getMemory("memory");
        // Set the subject to greet.
        byte[] subject = "Wasmer".getBytes(StandardCharsets.UTF_8);

        // Allocate memory for the subject, and get a pointer to it.
        Integer input_pointer = (Integer) instance.exports.getFunction("allocate").apply(subject.length)[0];
        // Write the subject into the memory.
        {
            ByteBuffer memoryBuffer = memory.buffer();
            memoryBuffer.position(input_pointer);
            memoryBuffer.put(subject);
        }
        // Run the `greet` function. Give the pointer to the subject.
        Integer output_pointer = (Integer) instance.exports.getFunction("greet").apply(input_pointer)[0];
        // Read the result of the `greet` function.
        String result;
        {
            StringBuilder output = new StringBuilder();
            ByteBuffer memoryBuffer = memory.buffer();

            for (Integer i = output_pointer, max = memoryBuffer.limit(); i < max; ++i) {
                byte[] b = new byte[1];
                memoryBuffer.position(i);
                memoryBuffer.get(b);

                if (b[0] == 0) {
                    break;
                }
                output.appendCodePoint(b[0]);
            }

            result = output.toString();
        }
        assert result.equals("Hello, Wasmer!");
        instance.close();
    }

    private byte[] readBytesFromClasspath(String fileName) throws Exception {
        Path wasmPath = Paths.get(this.getClass().getClassLoader().getResource(fileName).getPath());
        return Files.readAllBytes(wasmPath);
    }
}
