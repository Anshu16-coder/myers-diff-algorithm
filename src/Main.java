import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        boolean known = args.length == 3
                && (args[0].equals("lines")
                || args[0].equals("highlight"));

        if (!known) {
            System.err.println("usage: Main lines|highlight A_PATH B_PATH");
            System.exit(2);
        }

        String command = args[0];
        String aPath = args[1];
        String bPath = args[2];

        try {
            List<byte[]> a = readLines(aPath);
            List<byte[]> b = readLines(bPath);

           
            List<int[]> trace = myersLines(a, b);

            List<Edit> edits = backtrack(
                    a.size(),
                    b.size(),
                    trace,
                    false
            );

            if (command.equals("lines")) {
                printLines(a, b, edits);
            } else {
                printHighlight(a, b, edits);
            }

        } catch (IOException e) {
            System.err.println(
                    "Error reading files: " + e.getMessage()
            );
            System.exit(2);
        }
    }

    static List<byte[]> readLines(String path) throws IOException {

        byte[] data = Files.readAllBytes(Path.of(path));

        List<byte[]> lines = new ArrayList<>();

        int start = 0;

        for (int i = 0; i < data.length; i++) {

            if (data[i] == '\n') {

                lines.add(
                        Arrays.copyOfRange(
                                data,
                                start,
                                i
                        )
                );

                start = i + 1;
            }
        }

        if (start < data.length) {

            lines.add(
                    Arrays.copyOfRange(
                            data,
                            start,
                            data.length
                    )
            );
        }

        return lines;
    }

    interface Equality {
        boolean equal(int x, int y);
    }

    static List<int[]> myers(
            int n,
            int m,
            Equality equal,
            boolean preferInsertOnTie) {

        int max = n + m;

        List<int[]> trace = new ArrayList<>();

        int[] v = new int[2 * max + 3];

        int offset = max + 1;

        Arrays.fill(
                v,
                Integer.MIN_VALUE / 4
        );

        v[offset + 1] = 0;

        for (int d = 0; d <= max; d++) {

            for (int k = -d; k <= d; k += 2) {

                int x;

                if (k == -d) {

                    x = v[k + 1 + offset];

                } else if (k == d) {

                    x = v[k - 1 + offset] + 1;

                } else {

                    int insertionX =
                            v[k + 1 + offset];

                    int deletionX =
                            v[k - 1 + offset] + 1;

                    if (insertionX > deletionX) {

                        x = insertionX;

                    } else if (deletionX > insertionX) {

                        x = deletionX;

                    } else if (preferInsertOnTie) {

                        x = insertionX;

                    } else {

                        x = deletionX;
                    }
                }

                int y = x - k;

                while (
                        x < n
                                && y < m
                                && equal.equal(x, y)
                ) {

                    x++;
                    y++;
                }

                v[k + offset] = x;

            
                if (x >= n && y >= m) {

                    trace.add(v.clone());

                    return trace;
                }
            }

            trace.add(v.clone());
        }

        return trace;
    }


    static List<int[]> myersLines(
            List<byte[]> a,
            List<byte[]> b) {

        return myers(
                a.size(),
                b.size(),
                (x, y) ->
                        Arrays.equals(
                                a.get(x),
                                b.get(y)
                        ),
                false
        );
    }

   

    static List<Edit> backtrack(
            int n,
            int m,
            List<int[]> trace,
            boolean preferInsertOnTie) {

        int max = n + m;
        int offset = max + 1;

        int x = n;
        int y = m;

        List<Edit> edits = new ArrayList<>();

      
        for (
                int d = trace.size() - 1;
                d > 0;
                d--
        ) {

            int[] previousV =
                    trace.get(d - 1);

            int k = x - y;

            int previousK;

         
            if (k == -d) {

                previousK = k + 1;

            } else if (k == d) {

                previousK = k - 1;

            } else {

                int insertionX =
                        previousV[k + 1 + offset];

                int deletionX =
                        previousV[k - 1 + offset] + 1;

                if (insertionX > deletionX) {

                    previousK = k + 1;

                } else if (deletionX > insertionX) {

                    previousK = k - 1;

                } else if (preferInsertOnTie) {

                    previousK = k + 1;

                } else {

                    previousK = k - 1;
                }
            }

            int previousX =
                    previousV[
                            previousK + offset
                    ];

            int previousY =
                    previousX - previousK;

           
            while (
                    x > previousX
                            && y > previousY
            ) {

                edits.add(
                        new Edit(
                                "KEEP",
                                x - 1,
                                y - 1
                        )
                );

                x--;
                y--;
            }

           
            if (x == previousX) {

                
                edits.add(
                        new Edit(
                                "INSERT",
                                -1,
                                y - 1
                        )
                );

                y--;

            } else {

                
                edits.add(
                        new Edit(
                                "DELETE",
                                x - 1,
                                -1
                        )
                );

                x--;
            }
        }

       
        while (x > 0 && y > 0) {

            edits.add(
                    new Edit(
                            "KEEP",
                            x - 1,
                            y - 1
                    )
            );

            x--;
            y--;
        }

       
        Collections.reverse(edits);

        return edits;
    }


    static void printLines(
            List<byte[]> a,
            List<byte[]> b,
            List<Edit> edits)
            throws IOException {

        int i = 0;

        while (i < edits.size()) {

            Edit edit = edits.get(i);

           
            if (edit.type.equals("KEEP")) {

                printLine(
                        ' ',
                        a.get(edit.aIndex)
                );

                i++;
                continue;
            }

          
            List<Edit> deletes = new ArrayList<>();
            List<Edit> inserts = new ArrayList<>();

            while (
                    i < edits.size()
                            &&
                    !edits.get(i)
                            .type
                            .equals("KEEP")
            ) {

                Edit current = edits.get(i);

                if (current.type.equals("DELETE")) {

                    deletes.add(current);

                } else {

                    inserts.add(current);
                }

                i++;
            }

           
            for (Edit delete : deletes) {

                printLine(
                        '-',
                        a.get(delete.aIndex)
                );
            }

            for (Edit insert : inserts) {

                printLine(
                        '+',
                        b.get(insert.bIndex)
                );
            }
        }
    }

  

    static void printHighlight(
            List<byte[]> a,
            List<byte[]> b,
            List<Edit> edits)
            throws IOException {

        int i = 0;

        while (i < edits.size()) {

            Edit edit = edits.get(i);

            
            if (edit.type.equals("KEEP")) {

                printLine(
                        ' ',
                        a.get(edit.aIndex)
                );

                i++;
                continue;
            }

         
            List<Edit> deletes = new ArrayList<>();
            List<Edit> inserts = new ArrayList<>();

            while (
                    i < edits.size()
                            &&
                    !edits.get(i)
                            .type
                            .equals("KEEP")
            ) {

                Edit current = edits.get(i);

                if (current.type.equals("DELETE")) {

                    deletes.add(current);

                } else {

                    inserts.add(current);
                }

                i++;
            }

          
            for (Edit delete : deletes) {

                printLine(
                        '-',
                        a.get(delete.aIndex)
                );
            }

          
            int pairs =
                    Math.min(
                            deletes.size(),
                            inserts.size()
                    );

            for (
                    int p = 0;
                    p < inserts.size();
                    p++
            ) {

                Edit insert = inserts.get(p);

                printLine(
                        '+',
                        b.get(insert.bIndex)
                );

              
                if (p < pairs) {

                    Edit delete =
                            deletes.get(p);

                    RangeResult result =
                            characterDiff(
                                    a.get(delete.aIndex),
                                    b.get(insert.bIndex)
                            );

                    printRanges(result);
                }
            }
        }
    }

 
  

    static RangeResult characterDiff(
            byte[] oldBytes,
            byte[] newBytes) {

        String oldText =
                new String(
                        oldBytes,
                        StandardCharsets.UTF_8
                );

        String newText =
                new String(
                        newBytes,
                        StandardCharsets.UTF_8
                );

    
        int[] oldCodePoints =
                oldText
                        .codePoints()
                        .toArray();

        int[] newCodePoints =
                newText
                        .codePoints()
                        .toArray();

     
        List<int[]> trace =
                myers(
                        oldCodePoints.length,
                        newCodePoints.length,
                        (x, y) ->
                                oldCodePoints[x]
                                        ==
                                newCodePoints[y],
                        false
                );

        List<Edit> edits =
                backtrack(
                        oldCodePoints.length,
                        newCodePoints.length,
                        trace,
                        false
                );

        List<Range> oldRanges =
                new ArrayList<>();

        List<Range> newRanges =
                new ArrayList<>();

        int oldPos = 0;
        int newPos = 0;

      
        for (Edit edit : edits) {

            if (edit.type.equals("KEEP")) {

                oldPos++;
                newPos++;

            } else if (
                    edit.type.equals("DELETE")
            ) {

                addPosition(
                        oldRanges,
                        oldPos
                );

                oldPos++;

            } else {

                addPosition(
                        newRanges,
                        newPos
                );

                newPos++;
            }
        }

        return new RangeResult(
                mergeRanges(oldRanges),
                mergeRanges(newRanges)
        );
    }

 

    static void addPosition(
            List<Range> ranges,
            int position) {

        ranges.add(
                new Range(
                        position,
                        position + 1
                )
        );
    }



    static List<Range> mergeRanges(
            List<Range> ranges) {

        if (ranges.isEmpty()) {
            return ranges;
        }

      
        ranges.sort(
                Comparator.comparingInt(
                        r -> r.start
                )
        );

        List<Range> result =
                new ArrayList<>();

        Range current = ranges.get(0);

        for (
                int i = 1;
                i < ranges.size();
                i++
        ) {

            Range next = ranges.get(i);

           
            if (next.start <= current.end) {

                current =
                        new Range(
                                current.start,
                                Math.max(
                                        current.end,
                                        next.end
                                )
                        );

            } else {

                result.add(current);
                current = next;
            }
        }

        result.add(current);

        return result;
    }



    static void printRanges(
            RangeResult result) {

        System.out.print("? ");

        printRangeList(
                result.oldRanges
        );

        System.out.print(" | ");

        printRangeList(
                result.newRanges
        );

        System.out.print("\n");
        System.out.flush();
    }

    static void printRangeList(
            List<Range> ranges) {

        if (ranges.isEmpty()) {

            System.out.print(".");
            return;
        }

        for (
                int i = 0;
                i < ranges.size();
                i++
        ) {

            if (i > 0) {
                System.out.print(",");
            }

            Range range =
                    ranges.get(i);

            System.out.print(
                    range.start
                            + "-"
                            + range.end
            );
        }
    }



    static void printLine(
            char prefix,
            byte[] line)
            throws IOException {

  
        System.out.write(
                (byte) prefix
        );

        System.out.write(line);

        System.out.write('\n');
    }

    static class Edit {

        String type;
        int aIndex;
        int bIndex;

        Edit(
                String type,
                int aIndex,
                int bIndex) {

            this.type = type;
            this.aIndex = aIndex;
            this.bIndex = bIndex;
        }
    }

    static class Range {

        int start;
        int end;

        Range(
                int start,
                int end) {

            this.start = start;
            this.end = end;
        }
    }

    static class RangeResult {

        List<Range> oldRanges;
        List<Range> newRanges;

        RangeResult(
                List<Range> oldRanges,
                List<Range> newRanges) {

            this.oldRanges = oldRanges;
            this.newRanges = newRanges;
        }
    }
}