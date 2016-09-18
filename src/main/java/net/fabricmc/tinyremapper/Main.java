package net.fabricmc.tinyremapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    public static void main(String[] rawArgs) {
        List<String> args = new ArrayList<String>(rawArgs.length);
        boolean reverse = false;
        boolean propagatePrivate = false;
        boolean removeFrames = false;
        Set<String> forcePropagation = Collections.emptySet();
        File forcePropagationFile = null;

        for (String arg : rawArgs) {
            if (arg.startsWith("--")) {
                int valueSepPos = arg.indexOf('=');

                String argKey = valueSepPos == -1 ? arg.substring(2) : arg.substring(2, valueSepPos);
                argKey = argKey.toLowerCase(Locale.US);

                switch (argKey.toLowerCase()) {
                    case "reverse":
                        System.err.println("WARNING: --reverse is not currently implemented!");
                        reverse = true;
                        break;
                    case "forcepropagation":
                        forcePropagationFile = new File(arg.substring(valueSepPos + 1));
                        break;
                    case "propagateprivate":
                        propagatePrivate = true;
                        break;
                    case "removeframes":
                        removeFrames = true;
                        break;
                    default:
                        System.out.println("invalid argument: "+arg+".");
                        System.exit(1);
                }
            } else {
                args.add(arg);
            }
        }

        if (args.size() < 5) {
            System.out.println("usage: <input> <output> <mappings> <from> <to> [<classpath>]... [--reverse] [--forcePropagation=<file>] [--propagatePrivate]");
            System.exit(1);
        }

        Path input = Paths.get(args.get(0));
        if (!Files.isReadable(input)) {
            System.out.println("Can't read input file "+input+".");
            System.exit(1);
        }

        Path output = Paths.get(args.get(1));

        Path mappings = Paths.get(args.get(2));
        if (!Files.isReadable(mappings) || Files.isDirectory(mappings)) {
            System.out.println("Can't read mappings file "+mappings+".");
            System.exit(1);
        }

        String fromM = args.get(3);
        String toM = args.get(4);

        Path[] classpath = new Path[args.size() - 5];

        for (int i = 0; i < classpath.length; i++) {
            classpath[i] = Paths.get(args.get(i + 3));
            if (!Files.isReadable(classpath[i])) {
                System.out.println("Can't read classpath file "+i+": "+classpath[i]+".");
                System.exit(1);
            }
        }

        if (forcePropagationFile != null) {
            forcePropagation = new HashSet<>();

            if (!forcePropagationFile.canRead()) {
                System.out.println("Can't read forcePropagation file "+forcePropagationFile+".");
                System.exit(1);
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(forcePropagationFile))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    line = line.trim();

                    if (line.isEmpty() || line.charAt(0) == '#') continue;

                    forcePropagation.add(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }

        long startTime = System.nanoTime();

        TinyRemapper remapper = TinyRemapper.newRemapper().withMappings(((classMap, fieldMap, methodMap) -> {
            try (BufferedReader reader = Files.newBufferedReader(mappings)) {
                TinyUtils.read(reader, fromM, toM, classMap, fieldMap, methodMap);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            System.out.printf("mappings: %d classes, %d methods, %d fields%n", classMap.size(), methodMap.size(), fieldMap.size());
        })).withForcedPropagation(forcePropagation)
                .propagatePrivate(propagatePrivate)
                .removeFrames(removeFrames)
                .build();

        try {
            OutputConsumerJar outputConsumer = new OutputConsumerJar(output.toFile());

            outputConsumer.addNonClassFiles(input.toFile());

            remapper.read(input);
            remapper.read(classpath);

            remapper.apply(input, outputConsumer);

            outputConsumer.finish();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        remapper.finish();

        System.out.printf("Finished after %.2f ms.\n", (System.nanoTime() - startTime) / 1e6);
    }
}