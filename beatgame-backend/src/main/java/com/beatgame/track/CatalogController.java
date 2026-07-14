package com.beatgame.track;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api")
public class CatalogController {

    @GetMapping("/genres")
    public List<String> getGenres() {
        return Arrays.stream(Genre.values()).map(Enum::name).toList();
    }

    @GetMapping("/decades")
    public List<Integer> getDecades() {
        return Arrays.stream(Decade.values()).map(d -> d.from).toList();
    }
}
