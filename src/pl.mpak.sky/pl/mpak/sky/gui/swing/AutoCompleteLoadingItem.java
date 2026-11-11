package pl.mpak.sky.gui.swing;

/**
 * ORBADA 1.3.4: pozycja "wczytywanie..." w liscie podpowiedzi, gdy dostawca laduje dane w tle (np. metody pakietu
 * ze slownika Oracle). Nie da sie jej wstawic do tekstu - Enter zamyka liste; po zaladowaniu dostawca wola
 * AutoCompleteText.refresh().
 */
public class AutoCompleteLoadingItem extends AutoCompleteItem {

  public AutoCompleteLoadingItem(String displayText) {
    super("", displayText);
  }

}
