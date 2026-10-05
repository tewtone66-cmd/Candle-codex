package dev.candle.codex.module;

/** One user-facing option of a module. Values are kept in primitive fields so reading them is free. */
public final class Setting {
    public enum Type { BOOL, NUM, COLOR, CHOICE }

    public final String id, name;
    public final Type type;
    public boolean bool;
    public double num;
    public int color;
    public int choice;
    public double min, max, step;
    public String[] choices;

    private Setting(String id, String name, Type type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }

    public static Setting bool(String id, String name, boolean def) {
        Setting s = new Setting(id, name, Type.BOOL);
        s.bool = def;
        return s;
    }

    public static Setting num(String id, String name, double def, double min, double max, double step) {
        Setting s = new Setting(id, name, Type.NUM);
        s.num = def;
        s.min = min;
        s.max = max;
        s.step = step;
        return s;
    }

    public static Setting color(String id, String name, int rgb) {
        Setting s = new Setting(id, name, Type.COLOR);
        s.color = rgb & 0xFFFFFF;
        return s;
    }

    public static Setting choice(String id, String name, String def, String... choices) {
        Setting s = new Setting(id, name, Type.CHOICE);
        s.choices = choices;
        for (int i = 0; i < choices.length; i++) if (choices[i].equals(def)) s.choice = i;
        return s;
    }

    public String choiceName() {
        return choices[choice];
    }

    public int i() {
        return (int) Math.round(num);
    }

    public String serialize() {
        return switch (type) {
            case BOOL -> Boolean.toString(bool);
            case NUM -> Double.toString(num);
            case COLOR -> String.format("#%06X", color & 0xFFFFFF);
            case CHOICE -> choices[choice];
        };
    }

    public void deserialize(String v) {
        if (v == null) return;
        try {
            switch (type) {
                case BOOL -> bool = Boolean.parseBoolean(v);
                case NUM -> num = Math.max(min, Math.min(max, Double.parseDouble(v)));
                case COLOR -> color = Integer.parseInt(v.replace("#", ""), 16) & 0xFFFFFF;
                case CHOICE -> {
                    for (int i = 0; i < choices.length; i++) if (choices[i].equals(v)) choice = i;
                }
            }
        } catch (NumberFormatException ignored) {
        }
    }
}
