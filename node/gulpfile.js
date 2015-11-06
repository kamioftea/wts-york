/**
 * Created by jeff on 23/03/2015.
 */
var fs = require('fs');
var gulp = require('gulp');
var path = require('path');
var sass = require('gulp-sass');
var uglify = require('gulp-uglify');
var concat = require('gulp-concat');
var postcss = require('gulp-postcss');
var sourcemaps = require('gulp-sourcemaps');
var rename = require('gulp-rename');
var autoprefixer = require('autoprefixer-core');
var merge = require('merge-stream');
var googleWebFonts = require('gulp-google-webfonts');

var paths = {
	styles:        ['./assets/styles/app.scss'],
	styleIncludes: [
		'./assets/styles',
		'./bower_components/foundation/scss',
		'./bower_components/components-font-awesome/scss'
	],
	scripts:       [
		'./bower_components/jquery/dist/jquery.js',
		'./bower_components/foundation/js/foundation/foundation.js',
		'./bower_components/foundation/js/foundation/foundation.alert.js',
		'./bower_components/fastclick/lib/fastclick.js',
		'./bower_components/bacon/dist/Bacon.js',
		'./bower_components/handlebars/handlebars.js',
		'./assets/scripts/*'
	],
	script_folder_root: './assets/scripts',
	scripts_destination: '../public/javascripts',
	fonts: [
		'./bower_components/components-font-awesome/fonts/*'
	],
	google_web_fonts: './assets/fonts.list',
	fonts_destination: '../public/fonts'
};

function getFolders(dir) {
    return fs.readdirSync(dir)
      .filter(function(file) {
        return fs.statSync(path.join(dir, file)).isDirectory();
      });
}

gulp.task('styles', function () {
	return gulp.src(paths.styles)
		.pipe(sourcemaps.init())
		.pipe(sass({includePaths: paths.styleIncludes}))
		.pipe(postcss([autoprefixer({browsers: ['last 2 version']})]))
		.pipe(concat('app.css'))
		.pipe(sourcemaps.write('.'))
		.pipe(gulp.dest('../public/stylesheets'))
});

gulp.task('fonts', function () {
	var nodeFonts = gulp.src(paths.fonts)
		.pipe(gulp.dest(paths.fonts_destination))

	var webFonts = gulp.src(paths.google_web_fonts)
        .pipe(googleWebFonts())
        .pipe(gulp.dest(paths.fonts_destination));

    return merge(nodeFonts, webFonts)
});

gulp.task('scripts', function () {
    var folders = getFolders(paths.script_folder_root);

    var modules = folders.map(function(folder) {
          // concat into foldername.js
          // write to output
          // minify
          // rename to folder.min.js
          // write to output again
          // SEE: https://github.com/gulpjs/gulp/blob/master/docs/recipes/running-task-steps-per-folder.md
          return gulp.src(path.join(paths.script_folder_root, folder, '/**/*.js'))
            .pipe(sourcemaps.init())
            .pipe(concat(folder + '.compiled.js'))
            .pipe(uglify())
            .pipe(rename(folder + '.min.js'))
            .pipe(sourcemaps.write('.'))
            .pipe(gulp.dest(paths.scripts_destination));
       });

	var root_and_bower = gulp.src(paths.scripts)
		.pipe(sourcemaps.init())
		.pipe(concat('app.js'))
		.pipe(uglify())
		.pipe(rename('app.min.js'))
		.pipe(sourcemaps.write('.'))
		.pipe(gulp.dest(paths.scripts_destination));

	var modernizr = gulp
		.src(['./bower_components/foundation/js/vendor/modernizr.js'])
		.pipe(sourcemaps.init())
        .pipe(uglify())
        .pipe(rename('modernizr.min.js'))
        .pipe(sourcemaps.write('.'))
		.pipe(gulp.dest(paths.scripts_destination))

	return merge(modules, root_and_bower, modernizr);
});

gulp.task('build', ['styles', 'scripts', 'fonts']);

gulp.task('watch', ['build'], function () {
	gulp.watch(paths.styles.concat(paths.styleIncludes), ['styles']);
	gulp.watch(paths.scripts, ['scripts']);
	gulp.watch(path.join(paths.script_folder_root, '/**/*.js'), ['scripts']);
});

gulp.task('default', ['watch']);
