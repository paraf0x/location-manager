package ua.favn.baseManager.base.util;

/**
 * String similarity helpers for fuzzy re-ranking.
 */
public final class StringSimilarity {

   private StringSimilarity() {
   }

   /**
    * Normalizes for comparison: lower case, underscores to spaces, trims.
    */
   public static String normalize(String s) {
      if (s == null) {
         return "";
      }
      return s.toLowerCase().replace('_', ' ').trim();
   }

   /**
    * Returns similarity in [0,1] using normalized Levenshtein distance.
    */
   public static double normalizedLevenshtein(String a, String b) {
      String x = normalize(a);
      String y = normalize(b);
      if (x.isEmpty() && y.isEmpty()) {
         return 1.0;
      }
      int max = Math.max(x.length(), y.length());
      if (max == 0) {
         return 1.0;
      }
      int dist = levenshtein(x, y);
      return 1.0 - (double) dist / (double) max;
   }

   public static int levenshtein(String a, String b) {
      int n = a.length();
      int m = b.length();
      if (n == 0) {
         return m;
      }
      if (m == 0) {
         return n;
      }
      int[] prev = new int[m + 1];
      int[] cur = new int[m + 1];
      for (int j = 0; j <= m; j++) {
         prev[j] = j;
      }
      for (int i = 1; i <= n; i++) {
         cur[0] = i;
         char ca = a.charAt(i - 1);
         for (int j = 1; j <= m; j++) {
            int cost = ca == b.charAt(j - 1) ? 0 : 1;
            cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
         }
         int[] tmp = prev;
         prev = cur;
         cur = tmp;
      }
      return prev[m];
   }
}
