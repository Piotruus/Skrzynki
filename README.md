# Skrzynki — Paper 1.21.4 (projekt źródłowy)

Wymagania: Java 21, Maven, serwer Paper 1.21.4.

## Kompilacja przez GitHub
Wgraj zawartość tego folderu do repozytorium. Dodaj plik `.github/workflows/build.yml` z workflow Maven albo uruchom lokalnie:

```bash
mvn clean package
```

Plik wynikowy: `target/Skrzynki.jar`.

## Komendy administratora
- `/case create <nazwa>` — tworzy konfigurację skrzynki.
- `/case get <nazwa> [ilość]` — daje specjalny przedmiot skrzynki; postawienie go rejestruje lokalizację skrzynki.
- `/case edit <nazwa>` — edytor nagród (włóż nagrody w sloty 1–45, zapisz przyciskiem).
- `/case chance <nazwa> <slot> <procent>` — ustawia szansę nagrody, np. `/case chance flowybox 1 0.5`.
- `/case give <gracz> <nazwa> [ilość]` — daje klucze do skrzynki.
- `/case giveall <nazwa> [ilość]` — daje klucze wszystkim online.
- `/case setlocation <nazwa>` — przypisuje lokalizację do bloku, na który patrzy administrator.
- `/case removelocation <nazwa>` — usuwa lokalizację.
- `/case delete <nazwa>`, `/case list`, `/case reload`, `/case stats`.

Uprawnienie administracyjne: `skrzynki.admin` (domyślnie OP).

## Ważne ograniczenia
To jest projekt źródłowy, nie gotowy, przetestowany plik JAR. Po kompilacji przetestuj go na kopii serwera. Model 3D z resource-packa nie jest jeszcze w pełni podłączony do animowanego obiektu w świecie; aktualnie specjalny przedmiot po postawieniu rejestruje zwykły blok skrzyni. Paczkę zasobów trzeba skonfigurować osobno dla serwera.
