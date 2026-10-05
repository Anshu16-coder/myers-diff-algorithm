import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    static final byte KEEP = 0;
    static final byte INSERT = 1;
    static final byte DELETE = 2;

    // Buffered raw output. System.out flushes on every '\n' write, which is very slow.
    static final OutputStream out =
            new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 1 << 16);

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

            // Map every distinct line to an int id so comparisons are O(1).
            Map<ByteBuffer, Integer> ids = new HashMap<>();
            int[] aIds = toIds(a, ids);
            int[] bIds = toIds(b, ids);
            ids = null;

            List<int[]> trace = myers(aIds, bIds, false);

            Edits edits = backtrack(aIds.length, bIds.length, trace, false);
            trace = null;

            if (command.equals("lines")) {
                printEdits(a, b, edits, false);
            } else {
                printEdits(a, b, edits, true);
            }

            out.flush();

        } catch (IOException e) {
            System.err.println(
                    "Error reading files: " + e.getMessage()
            );
            System.exit(2);
        }
    }

    // ============================================================
    // INPUT
    // ============================================================

    static List<byte[]> readLines(String path) throws IOException {

        byte[] data = Files.readAllBytes(Path.of(path));

        List<byte[]> lines = new ArrayList<>();

        int start = 0;

        for (int i = 0; i < data.length; i++) {

            if (data[i] == '\n') {

                lines.add(Arrays.copyOfRange(data, start, i));

                start = i + 1;
            }
        }

        if (start < data.length) {

            lines.add(Arrays.copyOfRange(data, start, data.length));
        }

        return lines;
    }

    static int[] toIds(List<byte[]> lines, Map<ByteBuffer, Integer> ids) {

        int[] result = new int[lines.size()];

        for (int i = 0; i < result.length; i++) {

            ByteBuffer key = ByteBuffer.wrap(lines.get(i));

            Integer id = ids.get(key);

            if (id == null) {
                id = ids.size();
                ids.put(key, id);
            }

            result[i] = id;
        }

        return result;
    }

    // ============================================================
    // MYERS
    // ============================================================

    /*
     * Shortest edit script search.
     *
     * trace.get(d) stores only the furthest-x values of the diagonals that
     * exist at step d: k = -d, -d+2, ..., d. Diagonal k is at index (k + d) / 2.
     * That is d + 1 ints per step instead of the whole V array.
     */
    static List<int[]> myers(
            int[] a,
            int[] b,
            boolean preferInsertOnTie) {

        int n = a.length;
        int m = b.length;
        int max = n + m;

        List<int[]> trace = new ArrayList<>();

        int[] v = new int[2 * max + 3];

        int offset = max + 1;

        Arrays.fill(v, Integer.MIN_VALUE / 4);

        v[offset + 1] = 0;

        for (int d = 0; d <= max; d++) {

            int[] row = new int[d + 1];

            for (int k = -d; k <= d; k += 2) {

                int x;

                if (k == -d) {

                    x = v[k + 1 + offset];

                } else if (k == d) {

                    x = v[k - 1 + offset] + 1;

                } else {

                    int insertionX = v[k + 1 + offset];
                    int deletionX = v[k - 1 + offset] + 1;

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

                while (x < n && y < m && a[x] == b[y]) {

                    x++;
                    y++;
                }

                v[k + offset] = x;
                row[(k + d) >> 1] = x;

                if (x >= n && y >= m) {

                    trace.add(row);

                    return trace;
                }
            }

            trace.add(row);
        }

        return trace;
    }

    // ============================================================
    // BACKTRACK
    // ============================================================

    /*
     * Compact edit script: type[i] and idx[i] for i in [start, end).
     * KEEP and DELETE: idx is the index in A.
     * INSERT: idx is the index in B.
     */
    static class Edits {

        byte[] type;
        int[] idx;
        int start;
        int end;
    }

    static Edits backtrack(
            int n,
            int m,
            List<int[]> trace,
            boolean preferInsertOnTie) {

        int cap = n + m;

        Edits e = new Edits();
        e.type = new byte[cap];
        e.idx = new int[cap];
        e.end = cap;

        int pos = cap;

        int x = n;
        int y = m;

        for (int d = trace.size() - 1; d > 0; d--) {

            int[] previousV = trace.get(d - 1);

            int k = x - y;

            int previousK;

            if (k == -d) {

                previousK = k + 1;

            } else if (k == d) {

                previousK = k - 1;

            } else {

                // previousV is step d-1; diagonal j is at index (j + d - 1) / 2.
                int insertionX = previousV[(k + d) >> 1];
                int deletionX = previousV[(k + d - 2) >> 1] + 1;

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

            int previousX = previousV[(previousK + d - 1) >> 1];

            int previousY = previousX - previousK;

            while (x > previousX && y > previousY) {

                pos--;
                e.type[pos] = KEEP;
                e.idx[pos] = x - 1;

                x--;
                y--;
            }

            if (x == previousX) {

                pos--;
                e.type[pos] = INSERT;
                e.idx[pos] = y - 1;

                y--;

            } else {

                pos--;
                e.type[pos] = DELETE;
                e.idx[pos] = x - 1;

                x--;
            }
        }

        while (x > 0 && y > 0) {

            pos--;
            e.type[pos] = KEEP;
            e.idx[pos] = x - 1;

            x--;
            y--;
        }

        e.start = pos;

        return e;
    }

    // ============================================================
    // PRINTING (Part A and Part B)
    // ============================================================

    static void printEdits(
            List<byte[]> a,
            List<byte[]> b,
            Edits e,
            boolean highlight)
            throws IOException {

        int i = e.start;

        while (i < e.end) {

            if (e.type[i] == KEEP) {

                printLine(' ', a.get(e.idx[i]));

                i++;
                continue;
            }

            // One complete change block: [i, j)
            int j = i;

            while (j < e.end && e.type[j] != KEEP) {
                j++;
            }

            int deleteCount = 0;
            int insertCount = 0;

            for (int p = i; p < j; p++) {

                if (e.type[p] == DELETE) {
                    deleteCount++;
                } else {
                    insertCount++;
                }
            }

            // Deletes first.
            for (int p = i; p < j; p++) {

                if (e.type[p] == DELETE) {
                    printLine('-', a.get(e.idx[p]));
                }
            }

            // Then inserts; in highlight mode, the p-th insert is paired
            // with the p-th delete.
            int pairs = Math.min(deleteCount, insertCount);

            int deletePointer = i;
            int insertNumber = 0;

            for (int p = i; p < j; p++) {

                if (e.type[p] != INSERT) {
                    continue;
                }

                printLine('+', b.get(e.idx[p]));

                if (highlight && insertNumber < pairs) {

                    while (e.type[deletePointer] != DELETE) {
                        deletePointer++;
                    }

                    RangeResult result =
                            characterDiff(
                                    a.get(e.idx[deletePointer]),
                                    b.get(e.idx[p])
                            );

                    deletePointer++;

                    printRanges(result);
                }

                insertNumber++;
            }

            i = j;
        }
    }

    // ============================================================
    // CHARACTER DIFF
    // ============================================================

    static RangeResult characterDiff(
            byte[] oldBytes,
            byte[] newBytes) {

        int[] oldCodePoints =
                new String(oldBytes, StandardCharsets.UTF_8)
                        .codePoints()
                        .toArray();

        int[] newCodePoints =
                new String(newBytes, StandardCharsets.UTF_8)
                        .codePoints()
                        .toArray();

        List<int[]> trace = myers(oldCodePoints, newCodePoints, false);

        Edits e = backtrack(
                oldCodePoints.length,
                newCodePoints.length,
                trace,
                false
        );

        List<Range> oldRanges = new ArrayList<>();
        List<Range> newRanges = new ArrayList<>();

        int oldPos = 0;
        int newPos = 0;

        for (int i = e.start; i < e.end; i++) {

            if (e.type[i] == KEEP) {

                oldPos++;
                newPos++;

            } else if (e.type[i] == DELETE) {

                oldRanges.add(new Range(oldPos, oldPos + 1));

                oldPos++;

            } else {

                newRanges.add(new Range(newPos, newPos + 1));

                newPos++;
            }
        }

        return new RangeResult(
                mergeRanges(oldRanges),
                mergeRanges(newRanges)
        );
    }

    static List<Range> mergeRanges(List<Range> ranges) {

        if (ranges.isEmpty()) {
            return ranges;
        }

        ranges.sort(Comparator.comparingInt(r -> r.start));

        List<Range> result = new ArrayList<>();

        Range current = ranges.get(0);

        for (int i = 1; i < ranges.size(); i++) {

            Range next = ranges.get(i);

            if (next.start <= current.end) {

                current = new Range(
                        current.start,
                        Math.max(current.end, next.end)
                );

            } else {

                result.add(current);
                current = next;
            }
        }

        result.add(current);

        return result;
    }

    // ============================================================
    // OUTPUT
    // ============================================================

    static void printRanges(RangeResult result) throws IOException {

        StringBuilder sb = new StringBuilder();

        sb.append("? ");

        appendRangeList(sb, result.oldRanges);

        sb.append(" | ");

        appendRangeList(sb, result.newRanges);

        sb.append('\n');

        out.write(sb.toString().getBytes(StandardCharsets.US_ASCII));
    }

    static void appendRangeList(StringBuilder sb, List<Range> ranges) {

        if (ranges.isEmpty()) {

            sb.append('.');
            return;
        }

        for (int i = 0; i < ranges.size(); i++) {

            if (i > 0) {
                sb.append(',');
            }

            Range range = ranges.get(i);

            sb.append(range.start).append('-').append(range.end);
        }
    }

    static void printLine(char prefix, byte[] line) throws IOException {

        // Write original bytes directly; preserves invalid UTF-8 and \r.
        out.write((byte) prefix);
        out.write(line);
        out.write('\n');
    }

    // ============================================================
    // DATA CLASSES
    // ============================================================

    static class Range {

        int start;
        int end;

        Range(int start, int end) {

            this.start = start;
            this.end = end;
        }
    }

    static class RangeResult {

        List<Range> oldRanges;
        List<Range> newRanges;

        RangeResult(List<Range> oldRanges, List<Range> newRanges) {

            this.oldRanges = oldRanges;
            this.newRanges = newRanges;
        }
    }
}
