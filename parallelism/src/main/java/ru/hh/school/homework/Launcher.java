package ru.hh.school.homework;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static java.util.Collections.reverseOrder;
import static java.util.Map.Entry.comparingByValue;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;

public class Launcher {
    private static final Path PATH = Path.of("C:\\Users\\Artem\\IdeaProjects\\hh-school1\\parallelism\\src\\main\\java\\ru\\hh\\school\\parallelism");
    private static final String EXTENSION = ".java";
    private static final Map<String, Long> cacheOfQuery = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException, InterruptedException {
        ExecutorService fileProcessService = Executors.newCachedThreadPool();
        ExecutorService searchProcessService = Executors.newCachedThreadPool();

        List<Path> directories = Files.walk(PATH)
                .filter(Files::isDirectory)
                .toList();

        directories.forEach(directory -> CompletableFuture.supplyAsync(() -> getTopTenWordInDirectory(directory), fileProcessService).thenAccept(map -> {
            Set<String> words = map.keySet();
            words.forEach(word -> CompletableFuture.supplyAsync(() -> naiveSearch(word), searchProcessService)
                    .thenAccept(searchResult -> {
                                System.out.println("Папка:" + directory + " Слово для поиска:" + word + "," + " КолВо результатов в Google:" + searchResult);
                            }
                    ));
        }));

        fileProcessService.awaitTermination(60, TimeUnit.MILLISECONDS);
        searchProcessService.awaitTermination(60, TimeUnit.MILLISECONDS);
        fileProcessService.shutdown();
        searchProcessService.shutdown();
    }

    private static Map<String, Long> getTopTenWordInDirectory(Path directory) {
        final Map<String, Long> wordsCount = new HashMap<>();
        try {
            Files.list(directory)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(EXTENSION))
                    .forEach(file -> addWordsToMap(file, wordsCount));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return wordsCount.entrySet()
                .stream()
                .sorted(comparingByValue(reverseOrder()))
                .limit(10)
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

    }

    private static long naiveSearch(String query) {
        if (cacheOfQuery.containsKey(query)) {
            return cacheOfQuery.get(query);
        }
        Document document = null;
        try {
            document = Jsoup
                    .connect("https://www.google.com/search?q=" + query)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.116 Safari/537.36") //
                    .get();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Element divResultStats = document.select("#result-stats").first();
        String text = divResultStats.text();
        String resultsPart = text.substring(0, text.indexOf('('));
        long frequencyOfQuery = Long.parseLong(resultsPart.replaceAll("[^0-9]", ""));
        cacheOfQuery.put(query, frequencyOfQuery);
        return frequencyOfQuery;
    }

    private static void addWordsToMap(Path filePath, Map<String, Long> wordsCount) {
        try {
            Files.lines(filePath)
                    .flatMap(line -> Stream.of(line.split("[^a-zA-Z0-9]")))
                    .filter(word -> word.length() > 3)
                    .collect(groupingBy(identity(), counting()))
                    .entrySet()
                    .stream()
                    .sorted(comparingByValue(reverseOrder()))
                    .limit(10)
                    .forEach(wordnew -> wordsCount.merge(wordnew.getKey(), wordnew.getValue(), Long::sum));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
