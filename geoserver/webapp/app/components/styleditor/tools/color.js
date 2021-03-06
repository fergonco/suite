/* global $ */
angular.module('gsApp.styleditor.color', [])
.directive('styleEditorColor', ['$log',
    function($log) {
      return {
        restrict: 'EA',
        scope: {
          editor: '=',
        },
        template:
          '<li class="styleditor-color">'+
            '<i class="icon-paint-format"></i>'+
            '<span>Color</span>'+
          '</li>',
        replace: true,
        controller: function($scope, $element) {
          $scope.colorPicker = $('.styleditor-color');
          $scope.colorPicker.spectrum({
            showPalette: true,
            showInitial: true,
            hideAfterPaletteSelect: true,
            palette: [],
            beforeShow: function(col) {
              // check selection, if a color initialize the color picker
              var ed = $scope.editor;
              var selection = ed.getSelection();
              if (selection != null  &&
                  selection.match(/#?[0-9a-f]{3,6}$/i) != null) {
                $scope.colorPicker.spectrum('set', selection);
              }
              return true;
            },
            change: function(col) {
              var hex = col.toHex();

              var ed = $scope.editor;
              if (ed.somethingSelected()) {
                // replace the selection
                ed.replaceSelection(hex, 'around');
              }
              else {
                // insert
                ed.replaceRange(hex, ed.getCursor());
              }
            }
          });
        }
      };
    }]);
