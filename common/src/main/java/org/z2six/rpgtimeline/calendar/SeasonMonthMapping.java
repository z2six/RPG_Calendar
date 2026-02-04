package org.z2six.rpgtimeline.calendar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simple season-to-month mapping (0-based month indices).
 */
public record SeasonMonthMapping(
        List<Integer> spring,
        List<Integer> summer,
        List<Integer> autumn,
        List<Integer> winter
) {

    public SeasonMonthMapping {
        spring = safeList(spring);
        summer = safeList(summer);
        autumn = safeList(autumn);
        winter = safeList(winter);
    }

    public static SeasonMonthMapping defaultForMonthCount(int monthCount) {
        int safeCount = Math.max(4, monthCount);
        int base = safeCount / 4;
        int remainder = safeCount % 4;

        List<Integer> spring = new ArrayList<>();
        List<Integer> summer = new ArrayList<>();
        List<Integer> autumn = new ArrayList<>();
        List<Integer> winter = new ArrayList<>();

        int index = 0;
        int springCount = base + (remainder-- > 0 ? 1 : 0);
        int summerCount = base + (remainder-- > 0 ? 1 : 0);
        int autumnCount = base + (remainder-- > 0 ? 1 : 0);
        int winterCount = base + (remainder-- > 0 ? 1 : 0);

        for (int i = 0; i < springCount; i++) {
            spring.add(index++);
        }
        for (int i = 0; i < summerCount; i++) {
            summer.add(index++);
        }
        for (int i = 0; i < autumnCount; i++) {
            autumn.add(index++);
        }
        for (int i = 0; i < winterCount; i++) {
            winter.add(index++);
        }

        return new SeasonMonthMapping(spring, summer, autumn, winter);
    }

    private static List<Integer> safeList(List<Integer> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(input));
    }
}
