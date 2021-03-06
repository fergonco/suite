angular.module('gsApp.workspaces.data', [
  'gsApp.workspaces.data.add',
  'gsApp.workspaces.data.delete',
  'gsApp.workspaces.data.update',
  'gsApp.core.utilities',
  'gsApp.alertpanel',
  'ngSanitize'
])
.config(['$stateProvider',
    function($stateProvider) {
      $stateProvider.state('workspace.data', {
        url: '/data',
        templateUrl: '/workspaces/detail/data.tpl.html',
        controller: 'WorkspaceDataCtrl'
      });
    }])
.controller('WorkspaceDataCtrl', ['$scope', '$rootScope', '$state',
  '$stateParams', '$modal', '$window', '$log', 'GeoServer',
    function($scope, $rootScope, $state, $stateParams, $modal, $log, $window,
      GeoServer) {

      var workspace = $scope.workspace;

      // Set stores list to window height
      $scope.storesListHeight = {'height': $window.innerHeight-250};

      GeoServer.datastores.get($scope.workspace).then(
        function(result) {
          $scope.datastores = result.data;
          $scope.datastores.forEach(function(ds) {
            if (ds.format.toLowerCase() === 'shapefile') {
              ds.sourcetype = 'shp';
            } else if (ds.kind.toLowerCase() === 'raster') {
              ds.sourcetype = 'raster';
            } else if (ds.type.toLowerCase() === 'database') {
              ds.sourcetype = 'database';
            }
          });
        });

      $scope.storeRemoved = function(storeToRemove) {
        var index = $scope.datastores.indexOf(storeToRemove);
        if (index > -1) {
          $scope.datastores.splice(index, 1);
        }
      };

      $scope.storeAdded = function(storeToAdd) {
        $scope.datastores.push(storeToAdd);
      };

      // See utilities.js pop directive - 1 popover open at a time
      var openPopoverStore;
      $scope.closePopovers = function(store) {
        if (openPopoverStore) {
          openPopoverStore.showSourcePopover = false;
        }
        if (openPopoverStore===store) {
          openPopoverStore.showSourcePopover = false;
        } else {
          store.showSourcePopover = true;
          openPopoverStore = store;
        }
      };

      $scope.selectStore = function(store) {
        $scope.selectedStore = store;
        GeoServer.datastores.getDetails($scope.workspace, store.name).then(
        function(result) {
          if (result.success) {
            var storeData = result.data;
            $scope.selectedStore.resources = storeData.resources;
            $scope.selectedStore.layers = storeData.layers;
            $scope.selectedStore.layers.forEach(function(lyr) {
              var url = GeoServer.map.thumbnail.get($scope.workspace,
                lyr.name, 60, 60);
              lyr.thumbnail = url + '&format=image/png';
            });
          } else {
            $rootScope.alerts = [{
              type: 'warning',
              message: 'Store details could not be loaded.',
              fadeout: true
            }];
          }
        });
      };

      $scope.addNewStore = function() {
        var modalInstance = $modal.open({
          templateUrl: '/workspaces/detail/modals/data.add.tpl.html',
          controller: 'WorkspaceAddDataCtrl',
          size: 'lg',
          resolve: {
            workspace: function() {
              return $scope.workspace;
            },
            storeAdded: function() {
              return $scope.storeAdded;
            }
          }
        });
      };

      $scope.deleteStore = function() {
        var modalInstance = $modal.open({
          templateUrl: '/workspaces/detail/modals/data.delete.tpl.html',
          controller: 'WorkspaceDeleteDataCtrl',
          size: 'md',
          resolve: {
            workspace: function() {
              return $scope.workspace;
            },
            store: function() {
              return $scope.selectedStore;
            },
            storeRemoved: function() {
              return $scope.storeRemoved;
            }
          }
        });
      };

      $scope.updateStore = function() {
        var modalInstance = $modal.open({
          templateUrl: '/workspaces/detail/modals/data.update.tpl.html',
          controller: 'WorkspaceUpdateDataCtrl',
          size: 'md',
          resolve: {
            workspace: function() {
              return $scope.workspace;
            },
            store: function() {
              return $scope.selectedStore;
            }
          }
        });
      };
    }]);